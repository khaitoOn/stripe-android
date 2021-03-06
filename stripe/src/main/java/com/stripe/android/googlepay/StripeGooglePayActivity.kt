package com.stripe.android.googlepay

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentConfiguration
import com.stripe.android.PaymentIntentResult
import com.stripe.android.Stripe
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.model.GooglePayResult
import com.stripe.android.model.PaymentIntent
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import org.json.JSONObject

/**
 * [StripeGooglePayActivity] is used to confirm a `PaymentIntent` using Google Pay.
 *
 * Use [StripeGooglePayLauncher] to start [StripeGooglePayActivity].
 *
 * See [Troubleshooting](https://developers.google.com/pay/api/android/support/troubleshooting)
 * for a guide to troubleshooting Google Pay issues.
 */
internal class StripeGooglePayActivity : AppCompatActivity() {
    private val paymentsClient: PaymentsClient by lazy {
        Wallet.getPaymentsClient(
            this,
            Wallet.WalletOptions.Builder()
                .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                .build()
        )
    }
    private val publishableKey: String by lazy {
        PaymentConfiguration.getInstance(this).publishableKey
    }
    private val stripe: Stripe by lazy { Stripe(this, publishableKey) }
    private val args: StripeGooglePayLauncher.Args by lazy {
        StripeGooglePayLauncher.Args.create(intent)
    }
    private val viewModel: StripeGooglePayViewModel by viewModels {
        StripeGooglePayViewModel.Factory(
            application,
            publishableKey,
            args
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        overridePendingTransition(0, 0)

        setResult(
            RESULT_OK,
            Intent().putExtras(
                StripeGooglePayLauncher.Result.Canceled.toBundle()
            )
        )

        viewModel.googlePayResult.observe(this) { googlePayResult ->
            googlePayResult?.let(::finishWithResult)
        }

        if (!viewModel.hasLaunched) {
            viewModel.hasLaunched = true
            startGooglePay(args.paymentIntent)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun startGooglePay(paymentIntent: PaymentIntent) {
        if (paymentIntent.confirmationMethod == PaymentIntent.ConfirmationMethod.Manual) {
            viewModel.updateGooglePayResult(
                StripeGooglePayLauncher.Result.Error(
                    RuntimeException(
                        "StripeGooglePayActivity requires a PaymentIntent with automatic confirmation."
                    )
                )
            )
        } else {
            isReadyToPay(
                viewModel.createPaymentDataRequestForPaymentIntentArgs()
            )
        }
    }

    /**
     * Check that Google Pay is available and ready
     */
    private fun isReadyToPay(paymentDataRequest: JSONObject) {
        paymentsClient.isReadyToPay(
            viewModel.createIsReadyToPayRequest()
        ).addOnCompleteListener { task ->
            runCatching {
                if (task.isSuccessful) {
                    payWithGoogle(paymentDataRequest)
                } else {
                    viewModel.updateGooglePayResult(
                        StripeGooglePayLauncher.Result.Unavailable
                    )
                }
            }.onFailure {
                viewModel.updateGooglePayResult(
                    StripeGooglePayLauncher.Result.Error(it)
                )
            }
        }
    }

    private fun payWithGoogle(paymentDataRequest: JSONObject) {
        AutoResolveHelper.resolveTask(
            paymentsClient.loadPaymentData(
                PaymentDataRequest.fromJson(paymentDataRequest.toString())
            ),
            this,
            LOAD_PAYMENT_DATA_REQUEST_CODE
        )
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOAD_PAYMENT_DATA_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    onGooglePayResult(data)
                }
                RESULT_CANCELED -> {
                    viewModel.updateGooglePayResult(
                        StripeGooglePayLauncher.Result.Canceled
                    )
                }
                AutoResolveHelper.RESULT_ERROR -> {
                    val status = AutoResolveHelper.getStatusFromIntent(data)
                    viewModel.updateGooglePayResult(
                        StripeGooglePayLauncher.Result.Error(
                            RuntimeException(
                                "Google Pay returned an error. See googlePayStatus property for more information."
                            ),
                            googlePayStatus = status
                        )
                    )
                }
                else -> {
                    viewModel.updateGooglePayResult(
                        StripeGooglePayLauncher.Result.Error(
                            RuntimeException(
                                "Google Pay returned an expected result code."
                            )
                        )
                    )
                }
            }
        } else {
            onPaymentResult(requestCode, data)
        }
    }

    private fun onPaymentResult(requestCode: Int, data: Intent?) {
        val isPaymentResult = stripe.onPaymentResult(
            requestCode,
            data,
            object : ApiResultCallback<PaymentIntentResult> {
                override fun onSuccess(result: PaymentIntentResult) {
                    viewModel.updateGooglePayResult(
                        StripeGooglePayLauncher.Result.PaymentIntent(result)
                    )
                }

                override fun onError(e: Exception) {
                    viewModel.updateGooglePayResult(
                        StripeGooglePayLauncher.Result.Error(
                            e,
                            paymentMethod = viewModel.paymentMethod
                        )
                    )
                }
            }
        )

        if (!isPaymentResult) {
            viewModel.updateGooglePayResult(
                StripeGooglePayLauncher.Result.Error(
                    RuntimeException("Unable to confirm the PaymentIntent.")
                )
            )
        }
    }

    private fun onGooglePayResult(data: Intent?) {
        val paymentData = data?.let { PaymentData.getFromIntent(it) }
        if (paymentData == null) {
            viewModel.updateGooglePayResult(
                StripeGooglePayLauncher.Result.Error(
                    IllegalArgumentException("Google Pay data was not available")
                )
            )
            return
        }

        val paymentDataJson = JSONObject(paymentData.toJson())
        val shippingInformation =
            GooglePayResult.fromJson(paymentDataJson).shippingInformation

        val params = PaymentMethodCreateParams.createFromGooglePay(paymentDataJson)
        viewModel.createPaymentMethod(params).observe(this) { result ->
            result?.fold(
                onSuccess = ::onPaymentMethodCreated,
                onFailure = {
                    viewModel.paymentMethod = null
                    viewModel.updateGooglePayResult(
                        StripeGooglePayLauncher.Result.Error(it)
                    )
                }
            )
        }
    }

    private fun onPaymentMethodCreated(paymentMethod: PaymentMethod) {
        viewModel.paymentMethod = paymentMethod
        confirmIntent(args.paymentIntent.clientSecret.orEmpty(), paymentMethod)
    }

    private fun confirmIntent(
        clientSecret: String,
        paymentMethod: PaymentMethod
    ) {
        stripe.confirmPayment(
            this,
            ConfirmPaymentIntentParams.createWithPaymentMethodId(
                paymentMethodId = paymentMethod.id.orEmpty(),
                clientSecret = clientSecret
            )
        )
    }

    private fun finishWithResult(result: StripeGooglePayLauncher.Result) {
        setResult(
            RESULT_OK,
            Intent().putExtras(
                result.toBundle()
            )
        )
        finish()
    }

    private companion object {
        private const val LOAD_PAYMENT_DATA_REQUEST_CODE = 4444
    }
}
