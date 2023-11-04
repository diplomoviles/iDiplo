package com.amaurypm.idiplo

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.amaurypm.idiplo.databinding.ActivityLoginBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.Executor


class Login : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    //Para el lector biométrico
    private var banderaLectorHuella = true //el disp. cuenta con lector
    private var ingresoConHuella = false
    private var textoErrorLectorHuella = "" //para los mensajes de error
    private lateinit var biometricManager: BiometricManager
    private lateinit var executor: Executor //Lo requiere el prompt biométrico

    //Para Firebase
    private lateinit var firebaseAuth: FirebaseAuth

    //SharedPreferences Encriptadas:
    private lateinit var encryptedSharedPreferences: EncryptedSharedPreferences
    private lateinit var encryptedSharedPrefsEditor: SharedPreferences.Editor

    //Para la autenticación con huella activa
    private var usuarioSp: String? = ""
    private var contraseniaSp: String? = ""

    //Para las cajas de texto
    private var email = ""
    private var contrasenia = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        biometricManager = BiometricManager.from(this)
        executor = ContextCompat.getMainExecutor(this)

        firebaseAuth = FirebaseAuth.getInstance()

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
        } catch (e: GeneralSecurityException) {
            e.printStackTrace()
            Log.d("LOGS", "Error: ${e.message}")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d("LOGS", "Error: ${e.message}")
        }

        encryptedSharedPrefsEditor = encryptedSharedPreferences.edit()

        usuarioSp = encryptedSharedPreferences.getString("usuarioSp", "0")
        contraseniaSp = encryptedSharedPreferences.getString("constraseniaSp", "0")

        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d("LOGS", "La aplicación puede autenticar usando biometría")
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                banderaLectorHuella = false
                textoErrorLectorHuella = "El dispositivo no cuenta con lector de huella digital"
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                banderaLectorHuella = false
                textoErrorLectorHuella = "El lector de huella no está disponible actualmente"
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                banderaLectorHuella = false
                textoErrorLectorHuella =
                    "Favor de asociar una huella digital al dispositivo primeramente"
            }
        }

        binding.ibtnHuella.setOnClickListener {
            if (usuarioSp == "0") {
                Toast.makeText(
                    this,
                    "Ningún usuario activo para ingresar con huella digital",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                showBiometricPrompt()
            }
        }

        binding.btnLogin.setOnClickListener {
            if(!validaCampos()) return@setOnClickListener

            binding.progressBar.visibility = View.VISIBLE

            //autenticando al usuario
            autenticaUsuario(email, contrasenia)
        }

        binding.btnRegistrarse.setOnClickListener {
            if(!validaCampos()) return@setOnClickListener

            binding.progressBar.visibility = View.VISIBLE

            //Registrando al usuario
            firebaseAuth.createUserWithEmailAndPassword(email, contrasenia).addOnCompleteListener { authResult->
                if(authResult.isSuccessful){
                    //Enviar correo para verificación de email
                    var user_fb = firebaseAuth.currentUser
                    user_fb?.sendEmailVerification()?.addOnSuccessListener {
                        Toast.makeText(this, "El correo de verificación ha sido enviado", Toast.LENGTH_SHORT).show()
                    }?.addOnFailureListener {
                        Toast.makeText(this, "No se pudo enviar el correo de verificación", Toast.LENGTH_SHORT).show()
                    }

                    Toast.makeText(this, "Usuario creado", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("psw", contrasenia)
                    startActivity(intent)
                    finish()


                }else{
                    binding.progressBar.visibility = View.GONE
                    manejaErrores(authResult)
                }
            }
        }

        binding.tvRestablecerPassword.setOnClickListener {
            val resetMail = EditText(it.context)
            resetMail.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

            val passwordResetDialog = AlertDialog.Builder(it.context)
                .setTitle("Restablecer contraseña")
                .setMessage("Ingrese su correo para recibir el enlace para restablecer")
                .setView(resetMail)
                .setPositiveButton("Enviar") { _, _ ->
                    val mail = resetMail.text.toString()
                    if (mail.isNotEmpty()) {
                        firebaseAuth.sendPasswordResetEmail(mail).addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "El enlace para restablecer la contraseña ha sido enviado a su correo",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }.addOnFailureListener {
                            Toast.makeText(
                                this,
                                "El enlace no se ha podido enviar: ${it.message}",
                                Toast.LENGTH_SHORT
                            )
                                .show() //it tiene la excepción
                        }
                    } else {
                        Toast.makeText(
                            this,
                            "Favor de ingresar la dirección de correo",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }.setNegativeButton("Cancelar") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
        }
    }

    private fun showBiometricPrompt() {
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Autenticación")
                .setSubtitle("Ingrese usando su huella digital para iDiplo")
                .setNegativeButtonText("Cancelar")
                .build()

            val biometricPrompt =
                BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)

                        if (banderaLectorHuella) {
                            Toast.makeText(
                                applicationContext,
                                "No se pudo autenticar",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                applicationContext,
                                textoErrorLectorHuella,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        val authenticatedCryptoObject = result.cryptoObject
                        //Autenticación exitosa

                        binding.progressBar.visibility = View.VISIBLE
                        ingresoConHuella = true
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        //Toast.makeText(applicationContext, "Autenticación fallida", Toast.LENGTH_SHORT).show()
                    }
                })

            //Desplegando el biometric prompt
            biometricPrompt.authenticate(promptInfo)
        } else {
            Toast.makeText(applicationContext, textoErrorLectorHuella, Toast.LENGTH_SHORT).show()
        }
    }

    private fun validaCampos(): Boolean{
        email = binding.tietEmail.text.toString().trim() //para que quite espacios en blanco
        contrasenia = binding.tietContrasenia.text.toString().trim()

        if(email.isEmpty()){
            binding.tietEmail.error = "Se requiere el correo"
            binding.tietEmail.requestFocus()
            return false
        }

        if(contrasenia.isEmpty() || contrasenia.length < 6){
            binding.tietContrasenia.error = "Se requiere una contraseña o la contraseña no tiene por lo menos 6 caracteres"
            binding.tietContrasenia.requestFocus()
            return false
        }

        return true
    }

    private fun manejaErrores(task: Task<AuthResult>){
        var errorCode = ""

        try{
            errorCode = (task.exception as FirebaseAuthException).errorCode
        }catch(e: Exception){
            e.printStackTrace()
        }

        when(errorCode){
            "ERROR_INVALID_EMAIL" -> {
                Toast.makeText(this, "Error: El correo electrónico no tiene un formato correcto", Toast.LENGTH_SHORT).show()
                binding.tietEmail.error = "Error: El correo electrónico no tiene un formato correcto"
                binding.tietEmail.requestFocus()
            }
            "ERROR_WRONG_PASSWORD" -> {
                Toast.makeText(this, "Error: La contraseña no es válida", Toast.LENGTH_SHORT).show()
                binding.tietContrasenia.error = "La contraseña no es válida"
                binding.tietContrasenia.requestFocus()
                binding.tietContrasenia.setText("")

            }
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> {
                //An account already exists with the same email address but different sign-in credentials. Sign in using a provider associated with this email address.
                Toast.makeText(this, "Error: Una cuenta ya existe con el mismo correo, pero con diferentes datos de ingreso", Toast.LENGTH_SHORT).show()
            }
            "ERROR_EMAIL_ALREADY_IN_USE" -> {
                Toast.makeText(this, "Error: el correo electrónico ya está en uso con otra cuenta.", Toast.LENGTH_LONG).show()
                binding.tietEmail.error = ("Error: el correo electrónico ya está en uso con otra cuenta.")
                binding.tietEmail.requestFocus()
            }
            "ERROR_USER_TOKEN_EXPIRED" -> {
                Toast.makeText(this, "Error: La sesión ha expirado. Favor de ingresar nuevamente.", Toast.LENGTH_LONG).show()
            }
            "ERROR_USER_NOT_FOUND" -> {
                Toast.makeText(this, "Error: No existe el usuario con la información proporcionada.", Toast.LENGTH_LONG).show()
            }
            "ERROR_WEAK_PASSWORD" -> {
                Toast.makeText(this, "La contraseña porporcionada es inválida", Toast.LENGTH_LONG).show()
                binding.tietContrasenia.error = "La contraseña debe de tener por lo menos 6 caracteres"
                binding.tietContrasenia.requestFocus()
            }
            "NO_NETWORK" -> {
                Toast.makeText(this, "Red no disponible o se interrumpió la conexión", Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(this, "Error. No se pudo autenticar exitosamente.", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun autenticaUsuario(usr: String, psw: String){
        firebaseAuth.signInWithEmailAndPassword(usr, psw).addOnCompleteListener { authResult ->
            if(authResult.isSuccessful){
                Toast.makeText(this, "Autenticación exitosa", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("psw", psw)
                startActivity(intent)
                finish()
            }else{
                binding.progressBar.visibility = View.GONE
                manejaErrores(authResult)
            }
        }
    }

}