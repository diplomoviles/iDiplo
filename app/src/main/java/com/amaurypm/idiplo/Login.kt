package com.amaurypm.idiplo

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.amaurypm.idiplo.databinding.ActivityLoginBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

class Login : AppCompatActivity() {


    private lateinit var binding: ActivityLoginBinding

    //Para firebase auth
    private lateinit var firebaseAuth: FirebaseAuth

    //Propiedades para el email y la contraseña
    private var email = ""
    private var contrasena = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        //Bloquea la rotación y especifica la vista en modo retrato
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        //Instanciamos el objeto firebase auth
        firebaseAuth = FirebaseAuth.getInstance()

        //Si ya estaba previamente autenticado un usuario
        //Lo mandamos a la pantalla principal
        if(firebaseAuth.currentUser != null)
            actionLoginSuccessful()

        binding.btnLogin.setOnClickListener {
            if(!validateFields()) return@setOnClickListener

            binding.progressBar.visibility = View.VISIBLE

            authenticateUser(email, contrasena)
        }

        binding.btnRegistrarse.setOnClickListener {
            if(!validateFields()) return@setOnClickListener

            binding.progressBar.visibility = View.VISIBLE

            createUser(email, contrasena)
        }

        binding.tvRestablecerPassword.setOnClickListener {
            resetPassword()
        }

    }

    private fun validateFields(): Boolean{
        email = binding.tietEmail.text.toString().trim()  //Elimina los espacios en blanco
        contrasena = binding.tietContrasena.text.toString().trim()

        //Verifica que el campo de correo no esté vacío
        if(email.isEmpty()){
            binding.tietEmail.error = "Se requiere el correo"
            binding.tietEmail.requestFocus()
            return false
        }else if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            binding.tietEmail.error = "El correo no tiene un formato válido"
            binding.tietEmail.requestFocus()
            return false
        }

        //Verifica que el campo de la contraseña no esté vacía y tenga al menos 6 caracteres
        if(contrasena.isEmpty()){
            binding.tietContrasena.error = "Se requiere una contraseña"
            binding.tietContrasena.requestFocus()
            return false
        }else if(contrasena.length < 6){
            binding.tietContrasena.error = "La contraseña debe tener al menos 6 caracteres"
            binding.tietContrasena.requestFocus()
            return false
        }
        return true
    }

    private fun handleErrors(task: Task<AuthResult>){
        var errorCode = ""

        try{
            errorCode = (task.exception as FirebaseAuthException).errorCode
        }catch(e: Exception){
            e.printStackTrace()
        }

        when(errorCode){
            "ERROR_INVALID_EMAIL" -> {
                message("Error: El correo electrónico no tiene un formato correcto")
                binding.tietEmail.error = "Error: El correo electrónico no tiene un formato correcto"
                binding.tietEmail.requestFocus()
            }
            "ERROR_WRONG_PASSWORD" -> {
                message("Error: La contraseña no es válida")
                binding.tietContrasena.error = "La contraseña no es válida"
                binding.tietContrasena.requestFocus()
                binding.tietContrasena.setText("")

            }
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> {
                //An account already exists with the same email address but different sign-in credentials. Sign in using a provider associated with this email address.
                message("Error: Una cuenta ya existe con el mismo correo, pero con diferentes datos de ingreso")
            }
            "ERROR_EMAIL_ALREADY_IN_USE" -> {
                message("Error: el correo electrónico ya está en uso con otra cuenta.")
                binding.tietEmail.error = ("Error: el correo electrónico ya está en uso con otra cuenta.")
                binding.tietEmail.requestFocus()
            }
            "ERROR_USER_TOKEN_EXPIRED" -> {
                message("Error: La sesión ha expirado. Favor de ingresar nuevamente.")
            }
            "ERROR_USER_NOT_FOUND" -> {
                message("Error: No existe el usuario con la información proporcionada.")
            }
            "ERROR_WEAK_PASSWORD" -> {
                message("La contraseña porporcionada es inválida")
                binding.tietContrasena.error = "La contraseña debe de tener por lo menos 6 caracteres"
                binding.tietContrasena.requestFocus()
            }
            "NO_NETWORK" -> {
                message("Red no disponible o se interrumpió la conexión")
            }
            else -> {
                message("Error. No se pudo autenticar exitosamente.")
            }
        }

    }

    private fun actionLoginSuccessful(){
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun authenticateUser(usr: String, psw: String){
        firebaseAuth.signInWithEmailAndPassword(usr, psw).addOnCompleteListener { authResult ->
            if(authResult.isSuccessful){
                message("Autenticación exitosa")
                actionLoginSuccessful()
            }else{
                //Para que no se muestre el progress bar
                binding.progressBar.visibility = View.GONE
                handleErrors(authResult)
            }
        }
    }

    private fun createUser(usr: String, psw: String){
        firebaseAuth.createUserWithEmailAndPassword(usr, psw).addOnCompleteListener { authResult ->
            if(authResult.isSuccessful){
                //Sí se pudo registrar el usuario nuevo

                //Mandamos un correo de verificación
                firebaseAuth.currentUser?.sendEmailVerification()?.addOnSuccessListener {
                    message("El correo de verificación ha sido enviado")
                }?.addOnFailureListener {
                    message("No se pudo enviar el correo de verificación")
                }

                message("Usuario creado exitosamente")
                actionLoginSuccessful()
            }else{
                binding.progressBar.visibility = View.GONE
                handleErrors(authResult)
            }
        }
    }

    private fun resetPassword(){
        //Genero un edit text programáticamente
        val resetMail = EditText(this)
        resetMail.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        AlertDialog.Builder(this)
            .setTitle("Restablecer contraseña")
            .setMessage("Ingrese su correo para recibir el enlace de restablecimiento")
            .setView(resetMail)
            .setPositiveButton("Enviar") { _, _ ->
                val mail = resetMail.text.toString()
                if(mail.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(mail).matches()){
                    //Enviamos el enlace de restablecimiento
                    firebaseAuth.sendPasswordResetEmail(mail).addOnCompleteListener {
                        message("El enlace para restablecer la contraseña ha sido enviado")
                    }.addOnFailureListener {
                        message("No se ha podido enviar el mensaje")
                    }
                }else{
                    message("Favor de ingresar una dirección de correo válida")
                }
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

}