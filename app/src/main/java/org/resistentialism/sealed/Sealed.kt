package org.resistentialism.sealed

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View

import kotlinx.android.synthetic.main.activity_sealed.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

class Sealed : AppCompatActivity() {

    val CHANNEL_ID = "sealed-notifications"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sealed)
        setSupportActionBar(toolbar)
        createNotificationChannel()
        val preferences = getSharedPreferences("sealed", Context.MODE_PRIVATE)
        registerInBackground(preferences)
        fab.setOnClickListener { view ->
            val userId = preferences.getString(Constants.USER_ID, null)
            fetchMessagesInBackground(view, userId)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_sealed, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun registerInBackground(preferences: SharedPreferences) {
        val context = applicationContext
        val userId = getOrCreateUserId(preferences)
        Log.d("SEALED", "USERID: $userId")
        createUserInBackground(userId)
    }

    private fun getOrCreateUserId(preferences: SharedPreferences): String {
        var userId = preferences.getString(Constants.USER_ID, null)
        if (userId == null) {
            val editor = preferences.edit()
            userId = java.util.UUID.randomUUID().toString()
            editor.putString(Constants.USER_ID, userId)
            editor.commit()
        }
        return userId
    }

    private fun createUserInBackground(userId: String) {
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg params: Void): Void? {
                try {
                    createUser(userId)
                } catch (e: IOException) {
                    Log.e("SEALED", "registration failure", e)
                    e.printStackTrace()
                } catch (e: NoSuchAlgorithmException) {
                    Log.e("SEALED", "key generation failure", e)
                    e.printStackTrace()
                }

                return null
            }
        }.execute()
    }

    private fun fetchMessagesInBackground(view: View, userId: String) {
        val fetching = Snackbar.make(view, "Fetching messages", Snackbar.LENGTH_LONG)
        fetching.show()

        object : AsyncTask<Void, Void, List<Message>>() {
            override fun doInBackground(vararg params: Void): List<Message> {
                try {
                    return fetchMessages(userId).filterNotNull()
                } catch (e: IOException) {
                    Log.e("SEALED", "message fetch failure", e)
                    e.printStackTrace()
                }

                return ArrayList()
            }

            override fun onPostExecute(messages: List<Message>) {
                fetching.dismiss()
                messages.forEach { sendCopyNotification(it) }
            }
        }.execute()
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun fetchMessages(userId: String): Array<Message?> {
        val client = OkHttpClient()
        val url = getApiBaseUrl() + userId + "/messages"
        val request = Request.Builder().addHeader("Authorization", getApiKey()).url(url).build()
        val response = client.newCall(request).execute()
        val jsonMessages = JSONArray(response.body().string())

        val messages = arrayOfNulls<Message>(jsonMessages.length())

        for (i in 0 until jsonMessages.length()) {
            messages[i] = Message(jsonMessages.getJSONObject(i))
        }

        return messages
    }

    private fun getApiBaseUrl(): String {
        val preferences = getSharedPreferences("sealed", Context.MODE_PRIVATE)
        return preferences.getString(Constants.API_BASE_URL, Constants.DEFAULT_API_BASE_URL)
    }


    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun createUser(userId: String) {
        val client = OkHttpClient()

        val requestBody = FormBody.Builder()
                .add("public_key", getPublicKey())
                .add("api_key", getApiKey())
                .add("display_name", getDisplayName())
                .build()

        val request = Request.Builder().url(getApiBaseUrl() + userId).post(requestBody).build()

        val response = client.newCall(request).execute()

        Log.d("SEALED", "REGISTRATION RESPONSE CODE IS: " + response.code())
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun getPublicKey(): String {
        val preferences = getSharedPreferences("sealed", Context.MODE_PRIVATE)
        var publicKey = preferences.getString(Constants.PUBLIC_KEY, null)

        if (publicKey == null) {
            generateKeys(preferences)
            publicKey = getPublicKey()
        }

        Log.d("SEALED", "key has algorithm " + preferences.getString(Constants.KEY_ALGORITHM, null)!!)
        Log.d("SEALED", "public key has format " + preferences.getString(Constants.PUBLIC_KEY_FORMAT, null)!!)
        Log.d("SEALED", "private key has format " + preferences.getString(Constants.PRIVATE_KEY_FORMAT, null)!!)
        return publicKey
    }

    private fun getApiKey(): String {
        val preferences = getSharedPreferences("sealed", Context.MODE_PRIVATE)
        var apiKey = preferences.getString(Constants.API_KEY, null)

        if (apiKey == null) {
            apiKey = java.util.UUID.randomUUID().toString()
            val editor = preferences.edit()
            editor.putString(Constants.API_KEY, apiKey)
            editor.commit()
        }
        return apiKey
    }

    private fun getDisplayName(): String {
        val preferences = getSharedPreferences("sealed", Context.MODE_PRIVATE)
        return preferences.getString(Constants.DISPLAY_NAME, "James")
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun generateKeys(preferences: SharedPreferences) {
        val generator = KeyPairGenerator.getInstance("RSA")
        Log.d("SEALED", "starting key generation")
        generator.initialize(4096)
        val pair = generator.generateKeyPair()
        Log.d("SEALED", "finished key generation")
        val editor = preferences.edit()
        val publicKey = Base64.encodeToString(pair.public.encoded, Base64.DEFAULT)
        val privateKey = Base64.encodeToString(pair.private.encoded, Base64.DEFAULT)

        editor.putString(Constants.PUBLIC_KEY, publicKey)
        editor.putString(Constants.PRIVATE_KEY, privateKey)
        editor.putString(Constants.KEY_ALGORITHM, pair.public.algorithm)
        editor.putString(Constants.PUBLIC_KEY_FORMAT, pair.public.format)
        editor.putString(Constants.PRIVATE_KEY_FORMAT, pair.private.format)
        editor.commit()
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun sendCopyNotification(msg: Message) {

        val plainText = decryptMessage(msg)
        Log.d("SEALED", "CREATING NOTIFICATION")
        val builder = Notification.Builder(this, CHANNEL_ID)

        val copyIntent = Intent(this, CopyService::class.java)
        copyIntent.putExtra("text", plainText)
        val pendingIntent = PendingIntent.getService(this, 0, copyIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        builder.setActions(Notification.Action.Builder(copyIcon(), "Copy", pendingIntent).build())
        builder.setSmallIcon(R.drawable.abc_ic_menu_copy_mtrl_am_alpha)
        builder.setContentTitle(msg.name)
        val notification = builder.build()
        val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.COPY_NOTIFICATION_ID, notification)
    }

    private fun copyIcon(): Icon {
        return Icon.createWithResource("", R.drawable.abc_ic_menu_copy_mtrl_am_alpha)
    }

    private fun decryptMessage(msg: Message): String {
        return decryptBytes(Base64.decode(msg.body, Base64.DEFAULT));
    }

    @Throws(Exception::class)
    private fun decryptBytes(cipherBytes: ByteArray): String {
        val preferences = getSharedPreferences("sealed", Context.MODE_PRIVATE)
        val privateKeyString = preferences.getString(Constants.PRIVATE_KEY, null)
        if (privateKeyString == null) {
            Log.d("SEALED", "no private key found, cannot decrypt message")
            return ""
        }

        val privateKeyBytes = Base64.decode(privateKeyString, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val kf = KeyFactory.getInstance("RSA")
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        val privateKey = kf.generatePrivate(keySpec)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val plainTextBytes = cipher.doFinal(cipherBytes)
        return String(plainTextBytes)
    }

    private fun notificationManager(): NotificationManager {
        return this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sealed";
            val description = "Sealed passwords and secrets";
            val importance = NotificationManager.IMPORTANCE_DEFAULT;
            val channel = NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager().createNotificationChannel(channel)
        }
    }

}
