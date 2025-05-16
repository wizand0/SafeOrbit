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
        return view
    }

    override fun onResume() {
        super.onResume()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()

        // Повторная отрисовка даже если данные уже загружены
        val nameMap = viewModel.serverNameMap.value.orEmpty()
        val states = viewModel.mapStates.value.orEmpty()
        drawAll(states, nameMap)

        viewModel.serverNameMap.observe(viewLifecycleOwner) { nameMap ->
            viewModel.mapStates.value?.let { drawAll(it, nameMap) }
        }

        viewModel.mapStates.observe(viewLifecycleOwner) { states ->
            val nameMap = viewModel.serverNameMap.value.orEmpty()
            drawAll(states, nameMap)
        }

        viewModel.lastKnownCenter?.let {
            mapView.mapWindow.map.move(CameraPosition(it, 13.0f, 0.0f, 0.0f))
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        placemarks.clear()
        polylines.clear()
    }

    private fun drawAll(
        states: Map<String, ServerMapState>,
        nameMap: Map<String, String>
    ) {
        val map = mapView.mapWindow.map

        // Удаляем все старые объекты
        placemarks.values.forEach { map.mapObjects.remove(it) }
        polylines.values.forEach { map.mapObjects.remove(it) }

        // Очищаем внутренние списки
        placemarks.clear()
        polylines.clear()

        states.forEach { (serverId, state) ->
            val name = nameMap[serverId] ?: "Без имени"
            val point = state.latestPoint

            // --- Создание кастомной иконки ---
            val iconUri = viewModel.getIconUriForServer(serverId)
            val bitmap = createMarkerBitmap(serverId, name, iconUri)
            val imageProvider = ImageProvider.fromBitmap(bitmap)

            // --- Добавляем маркер ---
            val placemark = map.mapObjects.addPlacemark(point, imageProvider)

            // Обработка клика по маркеру
            placemark.addTapListener { _, _ ->
                activeMarkerId = serverId
                drawAll(viewModel.mapStates.value.orEmpty(), viewModel.serverNameMap.value.orEmpty())

                MarkerInfoBottomSheet(
                    serverId = serverId,
                    serverName = name,
                    point = point,
                    timestamp = state.timestamp,
                    iconUri = iconUri,
                    onDelete = { viewModel.deleteServer(it) }
                ).show(parentFragmentManager, "info")

                true
            }

            placemarks[serverId] = placemark

            // --- Добавляем полилинию маршрута, если есть история ---
            if (state.history.size >= 2) {
                val polyline = map.mapObjects.addPolyline(Polyline(state.history))
                polyline.setStrokeColor(state.color)
                polyline.setStrokeWidth(3f)
                polylines[serverId] = polyline
            }

            // --- Центрируем карту (только один раз для сервера) ---
            if (state.shouldCenter) {
                map.move(CameraPosition(point, 13.0f, 0.0f, 0.0f))
            }
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

        // Подсветка активного
        if (serverId == activeMarkerId) {
            view.setBackgroundResource(R.drawable.marker_background) // с рамкой
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