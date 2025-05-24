package com.amaurypm.idiplo

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.amaurypm.idiplo.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    //Para firebase
    private lateinit var firebaseAuth: FirebaseAuth
    private var user: FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        //Instanciamos el objeto firebase auth
        firebaseAuth = FirebaseAuth.getInstance()
        user = firebaseAuth.currentUser

        binding.tvUsuario.text = user?.email

        //revisamos si el correo no está verificado
        if(user?.isEmailVerified != true){
            //No ha verificado
            binding.tvCorreoNoVerificado.visibility = View.VISIBLE
            binding.btnReenviarVerificacion.visibility = View.VISIBLE

            binding.btnReenviarVerificacion.setOnClickListener {
                user?.sendEmailVerification()?.addOnSuccessListener {
                    message("El correo de verificación ha sido enviado")
                }?.addOnFailureListener {
                    message("No se pudo enviar el correo")
                }
            }
        }

        binding.btnCerrarSesion.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(this, Login::class.java))
            message("Sesión cerrada exitosamente")
            finish()
        }
    }
}