package ru.wizand.safeorbit.presentation.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.BuildConfig
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.data.model.LocationData

class MapFragment : Fragment() {

    private lateinit var mapView: MapView
    private val viewModel: ClientViewModel by activityViewModels()
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        if (!mapKitInitialized) {
            MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY)
            MapKitFactory.initialize(requireContext())
            mapKitInitialized = true
        }


        db = AppDatabase.getDatabase(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        mapView = view.findViewById(R.id.map_view)
        return view
    }

    override fun onResume() {
        super.onResume()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()

        // 🔽 Загружаем имена и обновляем карту
        lifecycleScope.launch {
            val servers = db.serverDao().getAll()
            val nameMap = servers.associateBy({ it.serverId }, { it.name })
            val locationMap = viewModel.getLatestLocationMap()
            updateMarkers(locationMap, nameMap)
        }
    }

    override fun onPause() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onPause()
    }

    fun updateMarkers(
        locationMap: Map<String, LocationData>,
        nameMap: Map<String, String>
    ) {
        val map = mapView.mapWindow.map
        map.mapObjects.clear()

        locationMap.forEach { (serverId, location) ->
            val point = Point(location.latitude, location.longitude)
            val name = nameMap[serverId] ?: "Без имени"
            val placemark = map.mapObjects.addPlacemark(
                point,
                ImageProvider.fromResource(requireContext(), R.drawable.red_dot)
            )
            placemark.setText(name)
        }

        locationMap.values.firstOrNull()?.let {
            val center = Point(it.latitude, it.longitude)
            map.move(CameraPosition(center, 13.0f, 0.0f, 0.0f))
        }
    }

    companion object {
        private var mapKitInitialized = false
    }
}

