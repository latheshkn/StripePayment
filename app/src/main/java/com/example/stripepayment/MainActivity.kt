package com.example.stripepayment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.GsonBuilder
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentIntentResult
import com.stripe.android.Stripe
import com.stripe.android.getPaymentIntentResult
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.model.StripeIntent
import com.stripe.android.view.CardInputWidget
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    private val backendUrl = "http://192.168.43.236/StripePayment/"
    private val httpClient = OkHttpClient()
    private lateinit var paymentIntentClientSecret: String
    private lateinit var stripe: Stripe
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Configure the SDK with your Stripe publishable key so it can make requests to Stripe
        stripe = Stripe(applicationContext, "pk_test_51J00GhSGMz1LDQsBxbJklx922aGBP940R1aW5QvVbLjzucZ1jSt4ynmFYrk0FuJ9Uw1yLSgz23VMMGPPsaKm5m6p003T4piuYJ")
        startCheckout()
    }

    private fun displayAlert(
        activity: Activity,
        title: String,
        message: String,
        restartDemo: Boolean = false
    ) {
        runOnUiThread {
            val builder = AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
            builder.setPositiveButton("Ok", null)
            builder.create().show()
        }
    }

    private fun startCheckout() {
        val weakActivity = WeakReference<Activity>(this)
        // Create a PaymentIntent by calling your server's endpoint.
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestJson = """
      {
          "currency":"Rupees",
          "items": [
              {"id":"xl-tshirt"}
          ]
      }
      """
        val body = requestJson.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(backendUrl + "create.php")
            .post(body)
            .build()
        httpClient.newCall(request)
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    weakActivity.get()?.let { activity ->
                        displayAlert(activity, "Failed to load page", "Error: $e")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        weakActivity.get()?.let { activity ->
                            displayAlert(
                                activity,
                                "Failed to load page",
                                "Error: $response"
                            )
                        }
                    } else {
                        val responseData = response.body?.string()
                        val responseJson =
                            responseData?.let { JSONObject(it) } ?: JSONObject()
                        // For added security, our sample app gets the publishable key
                        // from the server.
                        paymentIntentClientSecret = responseJson.getString("clientSecret")
                    }
                }
            })
        // Hook up the pay button to the card widget and stripe instance
        val payButton: Button = findViewById(R.id.payButton)
        payButton.setOnClickListener {
            val cardInputWidget =
                findViewById<CardInputWidget>(R.id.cardInputWidget)
            cardInputWidget.paymentMethodCreateParams?.let { params ->
                val confirmParams = ConfirmPaymentIntentParams
                    .createWithPaymentMethodCreateParams(params, paymentIntentClientSecret)
                stripe.confirmPayment(this, confirmParams)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val weakActivity = WeakReference<Activity>(this)
        // Handle the result of stripe.confirmPayment
        if (stripe.isPaymentResult(requestCode, data)) {

            lifecycleScope.launch {

                runCatching {
                    stripe.getPaymentIntentResult(requestCode, data!!).intent
                }.fold(
                    onSuccess = { paymentIntent ->
                        val status = paymentIntent.status
                        if (status == StripeIntent.Status.Succeeded) {
                            val gson = GsonBuilder().setPrettyPrinting().create()
                            weakActivity.get()?.let { activity ->
                                displayAlert(
                                    activity,
                                    "Payment succeeded",
                                    gson.toJson(paymentIntent)
                                )
                            }
                        } else if (status == StripeIntent.Status.RequiresPaymentMethod) {
                            weakActivity.get()?.let { activity ->
                                displayAlert(
                                    activity,
                                    "Payment failed",
                                    paymentIntent.lastPaymentError?.message.orEmpty()
                                )
                                Log.d("checkingPayment",paymentIntent.lastPaymentError?.message.orEmpty())

                            }
                        }
                    },
                    onFailure = {
                        weakActivity.get()?.let { activity ->

                            displayAlert(
                                activity,
                                "Payment failed",
                                it.toString()
                            )
                            Log.d("checkingPayment",it.toString())
                        }
                    }
                )
            }
        }
    }
}

