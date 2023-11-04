package com.amaurypm.idiplo

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.amaurypm.idiplo.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.io.IOException
import java.security.GeneralSecurityException


//Este de abajo NO
//import android.hardware.biometrics.BiometricManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var encryptedSharedPreferences: EncryptedSharedPreferences
    private lateinit var encryptedSharedPrefsEditor: SharedPreferences.Editor
    private var user: FirebaseUser? = null
    private var userId: String? = null
    private var banderaEmailVerificado = true
    private var banderaHuellaActiva = false
    private var psw = ""
    private lateinit var firebaseAuth: FirebaseAuth


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        user = firebaseAuth?.currentUser
        userId = user?.uid

        binding.tvUsuario.text = user?.email

        try {
            //Creando la llave para encriptar
            val masterKeyAlias = MasterKey.Builder(this, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            encryptedSharedPreferences = EncryptedSharedPreferences
                .create(
                    this,
                    "account",
                    masterKeyAlias,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ) as EncryptedSharedPreferences
        }catch(e: GeneralSecurityException){
            e.printStackTrace()
            Log.d("LOGS", "Error: ${e.message}")
        }catch (e: IOException){
            e.printStackTrace()
            Log.d("LOGS", "Error: ${e.message}")
        }

        encryptedSharedPrefsEditor = encryptedSharedPreferences.edit()

        //revisamos si el email no está verificado

        if(user?.isEmailVerified != true){
            banderaEmailVerificado = false
            binding.tvCorreoNoVerificado.visibility = View.VISIBLE
            binding.btnReenviarVerificacion.visibility = View.VISIBLE

            binding.btnReenviarVerificacion.setOnClickListener {
                user?.sendEmailVerification()?.addOnSuccessListener {
                    Toast.makeText(this, "El correo de verificación ha sido enviado", Toast.LENGTH_SHORT).show()
                }?.addOnFailureListener {
                    Toast.makeText(this, "Error: El correo de verificación no se ha podido enviar", Toast.LENGTH_SHORT).show()
                    Log.d("LOGS", "onFailure: ${it.message}")
                }
            }
        }

        binding.btnCerrarSesion.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(this, Login::class.java))
            finish()
        }
    }
}