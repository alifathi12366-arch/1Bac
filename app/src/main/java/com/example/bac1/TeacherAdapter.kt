package com.example.bac1

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

// موديل بيانات المدرس
data class TeacherModel(
    val id: String = "",
    val name: String = "",
    val subject: String = "",
    val phone: String = "",
    val studentsCount: Int = 0
)

class TeachersAdapter(private val teachersList: List<TeacherModel>) :
    RecyclerView.Adapter<TeachersAdapter.TeacherViewHolder>() {

    class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.teacherItemName)
        val tvSubject: TextView = itemView.findViewById(R.id.teacherItemSubject)
        val tvStudentsCount: TextView = itemView.findViewById(R.id.teacherItemStudentsCount)
        val tvPhone: TextView = itemView.findViewById(R.id.teacherItemPhone)
        val btnWhatsapp: Button = itemView.findViewById(R.id.btnWhatsappContact)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher, parent, false)
        return TeacherViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        val teacher = teachersList[position]

        holder.tvName.text = teacher.name
        holder.tvSubject.text = "المادة: ${teacher.subject}"
        holder.tvStudentsCount.text = "👥 ${teacher.studentsCount} طالب مسجل"
        holder.tvPhone.text = "📞 ${teacher.phone}"

        // فتح الواتساب للتواصل المباشر مع المدرس
        holder.btnWhatsapp.setOnClickListener {
            val context = holder.itemView.context
            val phoneNumber = teacher.phone.trim()

            if (phoneNumber.isNotEmpty()) {
                val message = "السلام عليكم يا أستاذ ${teacher.name}، أنا طالب في تطبيق 1Bac وعاوز أشترك مع حضرتك."
                val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(message)}"
                
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "تطبيق الواتساب غير مثبت لديك", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "رقم المدرس غير متاح حالياً", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = teachersList.size
    }
    
