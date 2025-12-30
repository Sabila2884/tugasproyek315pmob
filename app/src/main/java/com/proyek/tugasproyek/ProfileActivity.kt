package com.proyek.tugasproyek

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.cloudinary.utils.ObjectUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.proyek.tugasproyek.databinding.ActivityProfileBinding
import java.io.File

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var imageUri: Uri

    // Launcher galeri
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadImage(it) }
        }

    // Launcher kamera
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) uploadImage(imageUri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance()

        // ❗ AWAL: sembunyikan form & hasil
        binding.formProfile.visibility = View.GONE
        binding.resultProfile.visibility = View.GONE

        loadUserProfile()

        binding.ivBack.setOnClickListener { finish() }

        val imageClickListener = {
            if (checkPermissions()) showImagePickerDialog()
        }
        binding.imageProfile.setOnClickListener { imageClickListener() }
        binding.icAdd.setOnClickListener { imageClickListener() }

        // Edit profil
        binding.icEditProfile.setOnClickListener {
            binding.formProfile.visibility = View.VISIBLE
            binding.resultProfile.visibility = View.GONE
        }

        // Hapus profil
        binding.icDeleteProfile.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Hapus Profil")
                .setMessage("Apakah kamu yakin ingin menghapus semua data profil?")
                .setPositiveButton("Ya") { _, _ -> clearProfile() }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Simpan profil
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }

    private fun checkPermissions(): Boolean {
        val list = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) list.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) list.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (list.isNotEmpty()) {
            requestPermissions(list.toTypedArray(), 101)
            return false
        }
        return true
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Kamera", "Galeri")
        AlertDialog.Builder(this)
            .setTitle("Pilih Gambar")
            .setItems(options) { _, which ->
                if (which == 0) openCamera()
                else galleryLauncher.launch("image/*")
            }
            .show()
    }

    private fun openCamera() {
        val file = File.createTempFile(
            "profile_",
            ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        imageUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        cameraLauncher.launch(imageUri)
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.reference.child("users").child(uid)
            .addListenerForSingleValueEvent(object :
                com.google.firebase.database.ValueEventListener {

                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val name = snapshot.child("name").getValue(String::class.java)
                    val weight = snapshot.child("weight").getValue(String::class.java)
                    val height = snapshot.child("height").getValue(String::class.java)
                    val goal = snapshot.child("goal").getValue(String::class.java)
                    val url = snapshot.child("profileUrl").getValue(String::class.java)

                    if (name.isNullOrEmpty()) {
                        binding.formProfile.visibility = View.VISIBLE
                        binding.resultProfile.visibility = View.GONE
                    } else {
                        binding.formProfile.visibility = View.GONE
                        binding.resultProfile.visibility = View.VISIBLE
                    }

                    binding.etName.setText(name)
                    binding.etWeight.setText(weight)
                    binding.etHeight.setText(height)
                    binding.etGoal.setText(goal)

                    binding.tvUserName.text = name ?: "User"
                    binding.tvNameResult.text = "Nama: ${name ?: "-"}"
                    binding.tvWeightResult.text = "Berat Badan: ${weight ?: "-"}"
                    binding.tvHeightResult.text = "Tinggi Badan: ${height ?: "-"}"
                    binding.tvGoalResult.text = "Tujuan: ${goal ?: "-"}"

                    if (!url.isNullOrEmpty())
                        Glide.with(this@ProfileActivity).load(url)
                            .into(binding.imageProfile)
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    private fun uploadImage(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri) ?: return
        Thread {
            try {
                val result =
                    CloudinaryHelper.cloudinary.uploader()
                        .upload(inputStream, ObjectUtils.emptyMap())
                val url = result["secure_url"].toString()
                val uid = auth.currentUser?.uid ?: return@Thread
                db.reference.child("users").child(uid).child("profileUrl").setValue(url)

                runOnUiThread {
                    Glide.with(this).load(url).into(binding.imageProfile)
                    Toast.makeText(this, "Foto profil diperbarui", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Gagal upload gambar", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val weight = binding.etWeight.text.toString().trim()
        val height = binding.etHeight.text.toString().trim()
        val goal = binding.etGoal.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: return
        val ref = db.reference.child("users").child(uid)

        ref.child("name").setValue(name)
        ref.child("weight").setValue(weight)
        ref.child("height").setValue(height)
        ref.child("goal").setValue(goal)

        binding.tvUserName.text = name
        binding.tvNameResult.text = "Nama: $name"
        binding.tvWeightResult.text = "Berat Badan: $weight"
        binding.tvHeightResult.text = "Tinggi Badan: $height"
        binding.tvGoalResult.text = "Tujuan: $goal"

        binding.formProfile.visibility = View.GONE
        binding.resultProfile.visibility = View.VISIBLE

        Toast.makeText(this, "Profil berhasil disimpan", Toast.LENGTH_SHORT).show()
    }

    private fun clearProfile() {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.reference.child("users").child(uid)
        ref.removeValue()

        binding.formProfile.visibility = View.VISIBLE
        binding.resultProfile.visibility = View.GONE

        binding.etName.setText("")
        binding.etWeight.setText("")
        binding.etHeight.setText("")
        binding.etGoal.setText("")
        binding.imageProfile.setImageResource(R.drawable.account_svg)

        Toast.makeText(this, "Profil dihapus", Toast.LENGTH_SHORT).show()
    }
}
