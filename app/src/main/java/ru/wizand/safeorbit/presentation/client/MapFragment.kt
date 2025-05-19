package ru.wizand.safeorbit.presentation.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.PolylineMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import ru.wizand.safeorbit.BuildConfig
import ru.wizand.safeorbit.R

class MapFragment : Fragment() {

    private lateinit var mapView: MapView
    private val viewModel: ClientViewModel by activityViewModels()

    private val placemarks = mutableMapOf<String, PlacemarkMapObject>()
    private val polylines = mutableMapOf<String, PolylineMapObject>()
    private val handler = Handler(Looper.getMainLooper())

    private var activeMarkerId: String? = null

    private var hasCenteredOnAnyServer = false

    private var serversLoaded = false

    // ✳️ Добавлены кеши
    private var cachedStates: Map<String, ServerMapState>? = null
    private var cachedNames: Map<String, String>? = null

    private var isConnected = false
    private var hasShownConnectionToast = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!mapKitInitialized) {
            MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY)
            MapKitFactory.initialize(requireContext())
            mapKitInitialized = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        mapView = view.findViewById(R.id.map_view)
        view.findViewById<View>(R.id.loadingLayout)?.visibility = View.VISIBLE // ✳️ сразу показываем
        return view
    }

    override fun onResume() {
        super.onResume()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()

        observeViewModel()

        view?.findViewById<View>(R.id.loadingLayout)?.visibility = View.VISIBLE

//        viewModel.lastKnownCenter?.let {
//            mapView.mapWindow.map.move(CameraPosition(it, 13.0f, 0.0f, 0.0f))
//        }

        if (!serversLoaded) {
            viewModel.loadAndObserveServers()
            serversLoaded = true
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        placemarks.clear()
        polylines.clear()
    }

    private fun observeViewModel() {
        // 🔁 Следим за соединением с Firebase
        viewModel.isConnected.observe(viewLifecycleOwner) { connected ->
            isConnected = connected

            if (!connected && !hasShownConnectionToast) {
                handler.postDelayed({
                    if (!isConnected) {
                        Toast.makeText(requireContext(), "Нет подключения к Firebase", Toast.LENGTH_LONG).show()
                        hasShownConnectionToast = true
                    }
                }, 2000)
            } else if (connected) {
                maybeDraw() // подключились — пробуем рисовать
            }
        }

        viewModel.serverNameMap.observe(viewLifecycleOwner) { nameMap ->
            cachedNames = nameMap
            maybeDraw()
        }

        viewModel.mapStates.observe(viewLifecycleOwner) { states ->
            cachedStates = states
            maybeDraw()
        }
    }

    // ✳️ Метод, вызывающий drawAll только когда обе LiveData загружены
    private fun maybeDraw() {
        val view = view ?: return

        if (!isConnected) return

        val states = cachedStates
        val names = cachedNames

        if (states != null && names != null && states.isNotEmpty()) {
            drawAll(states, names)
            view.findViewById<View>(R.id.loadingLayout)?.visibility = View.GONE
        } else {
            // 💡 Скрываем индикатор если ничего не отрисовано спустя 5 секунд
            handler.postDelayed({
                if (view.findViewById<View>(R.id.loadingLayout)?.visibility == View.VISIBLE) {
                    view.findViewById<View>(R.id.loadingLayout)?.visibility = View.GONE
                    if ((states?.isEmpty() != false || names?.isEmpty() != false) && !hasShownConnectionToast) {
                        Toast.makeText(requireContext(), "Нет активных серверов или подключения", Toast.LENGTH_LONG).show()
                        hasShownConnectionToast = true
                    }
                }
            }, 5000)
        }
    }

    private fun drawAll(
        states: Map<String, ServerMapState>,
        nameMap: Map<String, String>
    ) {
        val map = mapView.mapWindow.map

        val currentIds = states.keys
        val existingIds = placemarks.keys

        (existingIds - currentIds).forEach { id ->
            placemarks[id]?.let { map.mapObjects.remove(it) }
            polylines[id]?.let { map.mapObjects.remove(it) }
            placemarks.remove(id)
            polylines.remove(id)
        }

        for ((serverId, state) in states) {
            val name = nameMap[serverId] ?: "Без имени"
            val point = state.latestPoint
            val iconUri = viewModel.getIconUriForServer(serverId)

            val existingPlacemark = placemarks[serverId]

            if (existingPlacemark != null) {
                val newBitmap = createMarkerBitmap(serverId, name, iconUri)
                existingPlacemark.setIcon(ImageProvider.fromBitmap(newBitmap))

                if (existingPlacemark.geometry != point) {
                    animateMarkerMove(existingPlacemark, existingPlacemark.geometry, point)
                }
            } else {
                val bitmap = createMarkerBitmap(serverId, name, iconUri)
                val placemark = map.mapObjects.addPlacemark(point, ImageProvider.fromBitmap(bitmap))
                placemarks[serverId] = placemark
            }

            if (state.history.size >= 2) {
                val polyline = polylines[serverId]
                if (polyline != null) {
                    polyline.geometry = Polyline(state.history)
                } else {
                    val newPolyline = map.mapObjects.addPolyline(Polyline(state.history))
                    newPolyline.setStrokeColor(state.color)
                    newPolyline.setStrokeWidth(3f)
                    polylines[serverId] = newPolyline
                }
            }

            if (state.shouldCenter) {
                map.move(CameraPosition(point, 13.0f, 0.0f, 0.0f))
            }
        }

        if (!hasCenteredOnAnyServer && states.isNotEmpty()) {
            val first = states.values.first()
            map.move(CameraPosition(first.latestPoint, 13.0f, 0.0f, 0.0f))
            hasCenteredOnAnyServer = true
        }


//        if (states.isNotEmpty() && viewModel.lastKnownCenter == null) {
//            val first = states.values.first()
//            map.move(CameraPosition(first.latestPoint, 13.0f, 0.0f, 0.0f))
//            viewModel.lastKnownCenter = first.latestPoint // на всякий случай
//        }
    }

    private fun updateMarkerHighlighting() {
        for ((serverId, placemark) in placemarks) {
            val name = viewModel.serverNameMap.value?.get(serverId) ?: "Без имени"
            val iconUri = viewModel.getIconUriForServer(serverId)
            val bitmap = createMarkerBitmap(serverId, name, iconUri)
            placemark.setIcon(ImageProvider.fromBitmap(bitmap))
        }
    }

    private fun createMarkerBitmap(serverId: String, name: String, iconUri: String?): Bitmap {
        val view = layoutInflater.inflate(R.layout.view_marker, null)

        val textView = view.findViewById<TextView>(R.id.marker_title)
        val imageView = view.findViewById<ImageView>(R.id.marker_icon)

        textView.text = name

        if (iconUri != null) {
            try {
                val bitmap = BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(Uri.parse(iconUri)))
                imageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                imageView.setImageResource(R.drawable.red_dot)
            }
        } else {
            imageView.setImageResource(R.drawable.red_dot)
        }

        if (serverId == activeMarkerId) {
            view.setBackgroundResource(R.drawable.marker_background)
            view.scaleX = 1.2f
            view.scaleY = 1.2f
        } else {
            view.scaleX = 1.0f
            view.scaleY = 1.0f
        }

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val output = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        view.draw(canvas)
        return output
    }

    private fun animateMarkerMove(marker: PlacemarkMapObject, from: Point, to: Point) {
        val duration = 300L
        val steps = 10
        val delay = duration / steps
        for (i in 1..steps) {
            handler.postDelayed({
                val lat = from.latitude + (to.latitude - from.latitude) * i / steps
                val lon = from.longitude + (to.longitude - from.longitude) * i / steps
                marker.geometry = Point(lat, lon)
            }, i * delay)
        }
    }

    companion object {
        private var mapKitInitialized = false
    }
}
