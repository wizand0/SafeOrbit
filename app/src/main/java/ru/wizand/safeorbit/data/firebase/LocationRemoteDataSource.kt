package ru.wizand.safeorbit.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import ru.wizand.safeorbit.data.model.LocationData

//class LocationRemoteDataSource(private val db: DatabaseReference = FirebaseDatabase.getInstance().reference) {

//    fun sendLocation(serverId: String, location: LocationData) {
//        db.child("servers").child(serverId).child("location").setValue(location)
//    }
//
//    fun observeLocation(serverId: String, callback: (LocationData?) -> Unit) {
//        db.child("servers").child(serverId).child("location")
//            .addValueEventListener(object : ValueEventListener {
//                override fun onDataChange(snapshot: DataSnapshot) {
//                    callback(snapshot.getValue(LocationData::class.java))
//                }
//
//                override fun onCancelled(error: DatabaseError) {}
//            })
//    }
//}
