package ru.wizand.safeorbit.presentation.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.runtime.image.ImageProvider
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.BuildConfig

class MapFragment : Fragment() {

    private lateinit var mapView: MapView
    private val viewModel: ClientViewModel by activityViewModels()
    private var marker: PlacemarkMapObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY)
        MapKitFactory.initialize(requireContext())
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.serverLocation.observe(viewLifecycleOwner) { location ->
            val point = Point(location.latitude, location.longitude)

            val map = mapView.mapWindow.map

            if (marker == null) {
                marker = map.mapObjects.addPlacemark(
                    point,
                    ImageProvider.fromResource(requireContext(), R.drawable.red_dot)
                )
            } else {
                marker?.geometry = point
            }

            map.move(CameraPosition(point, 15.0f, 0.0f, 0.0f))
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }
}
