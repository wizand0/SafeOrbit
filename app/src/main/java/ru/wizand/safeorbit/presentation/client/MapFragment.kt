// Автоматически обновлено: безопасный вызов context
package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    private var cachedStates: Map<String, ServerMapState>? = null
    private var cachedNames: Map<String, String>? = null
    private var isConnected = false
    private var hasShownConnectionToast = false

    private val permissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        if (!mapKitInitialized) {
            MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY)
            context?.let { MapKitFactory.initialize(it) }
            mapKitInitialized = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        mapView = view.findViewById(R.id.map_view)
        view.findViewById<View>(R.id.loadingLayout)?.visibility = View.VISIBLE
        return view
    }

    override fun onResume() {
        super.onResume()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()

        checkAndRequestPermissionsIfNeeded()

        Log.d("MAP", "onResume(), mapView started = ${::mapView.isInitialized}")
        observeViewModel()
        view?.findViewById<View>(R.id.loadingLayout)?.visibility = View.VISIBLE
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

    private fun checkAndRequestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) return

        // Показать пояснения по очереди
        if (android.Manifest.permission.ACCESS_FINE_LOCATION in notGranted) {
            AlertDialog.Builder(requireContext())
                .setTitle("Доступ к геопозиции")
                .setMessage("Разрешение на определение местоположения необходимо для отображения координат серверов на карте. Без него вы не сможете видеть, где находятся устройства.")
                .setPositiveButton("Продолжить") { _, _ ->
                    // После локации, проверим микрофон
                    if (android.Manifest.permission.RECORD_AUDIO in notGranted) {
                        showAudioPermissionDialog(notGranted)
                    } else {
                        permissionRequestLauncher.launch(notGranted.toTypedArray())
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else if (android.Manifest.permission.RECORD_AUDIO in notGranted) {
            // Если локация уже есть, но нет микрофона
            showAudioPermissionDialog(notGranted)
        }
    }

    private fun showAudioPermissionDialog(notGranted: List<String>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Доступ к микрофону")
            .setMessage("Разрешение на запись аудио нужно для функции \"аудионяни\" — вы сможете слышать звуки с устройства-сервера.")
            .setPositiveButton("Разрешить") { _, _ ->
                permissionRequestLauncher.launch(notGranted.toTypedArray())
            }
            .setNegativeButton("Отмена", null)
            .show()
    }



    private fun observeViewModel() {
        viewModel.isConnected.observe(viewLifecycleOwner) { connected ->
            isConnected = connected
            if (!connected && !hasShownConnectionToast) {
                handler.postDelayed({
                    if (!isConnected && isAdded && context != null) {
                        Toast.makeText(requireContext(), "Нет подключения к Firebase", Toast.LENGTH_LONG).show()
                        hasShownConnectionToast = true
                    }
                }, 2000)
            } else if (connected) {
                maybeDraw()
            }
        }

        viewModel.serverNameMap.observe(viewLifecycleOwner) { nameMap ->
            cachedNames = nameMap
            maybeDraw()
        }

        viewModel.mapStates.observe(viewLifecycleOwner) { states ->
            Log.d("MAP", "mapStates updated: ${states.size} items")
            cachedStates = states
            maybeDraw()
        }
    }

    private fun maybeDraw() {
        if (!isAdded || context == null || view == null) return

        val states = cachedStates
        val names = cachedNames


//        if (states != null && names != null && states.isNotEmpty()) {
        if (states != null && names != null && states.isNotEmpty()) {
            // 🔍 Проверим: есть ли в states хотя бы один id, который есть в names?
//            val validServers = states.keys.any { names.containsKey(it) }
            val validServers = states.keys
                .filterNot { it == "testServer" }
                .any { names.containsKey(it) }

            if (validServers) {
                Log.d("MAP", "🧠 maybeDraw(): valid=${validServers}, states=${states.size}, names=${names.size}")
                drawAll(states, names)
                view?.findViewById<View>(R.id.loadingLayout)?.visibility = View.GONE
                return
            }
        }

        // ⏱ Отложенная проверка
        handler.postDelayed({
            if (!isAdded || view == null || context == null) return@postDelayed

            val stillVisible = view?.findViewById<View>(R.id.loadingLayout)?.visibility == View.VISIBLE
            if (stillVisible) {
                view?.findViewById<View>(R.id.loadingLayout)?.visibility = View.GONE

                if (names == null || names.isEmpty()) {
                    Toast.makeText(requireContext(), "Нет добавленных серверов", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Не удалось загрузить данные с серверов", Toast.LENGTH_LONG).show()
                }
            }
        }, 4_000)
    }

    // Замена метода
    private fun drawAll(states: Map<String, ServerMapState>, nameMap: Map<String, String>) {
        val map = mapView.mapWindow.map
        val validStates = states.filter { (id, _) -> nameMap[id]?.isNotBlank() == true }

        // Удаляем маркеры и линии, которых больше нет
        val toRemove = placemarks.keys - validStates.keys
        toRemove.forEach { id ->
            placemarks.remove(id)?.let { map.mapObjects.remove(it) }
            polylines.remove(id)?.let { map.mapObjects.remove(it) }
        }

        for ((serverId, state) in validStates) {
            val name = nameMap[serverId] ?: continue
            val point = state.latestPoint
            val iconUri = viewModel.getIconUriForServer(serverId)

            val existingPlacemark = placemarks[serverId]
            val bitmap = createMarkerBitmap(serverId, name, iconUri)

            if (existingPlacemark == null) {
                val placemark = map.mapObjects.addPlacemark(point, ImageProvider.fromBitmap(bitmap))
                placemarks[serverId] = placemark
                placemark.addTapListener { _, _ ->
                    showDetails(serverId)
                    true
                }
            } else {
                if (existingPlacemark.geometry != point) {
                    animateMarkerMove(existingPlacemark, existingPlacemark.geometry, point)
                }
                existingPlacemark.setIcon(ImageProvider.fromBitmap(bitmap))
            }

            val history = state.history
            if (history.size >= 2) {
                val polyline = polylines[serverId]
                if (polyline == null) {
                    val newPolyline = map.mapObjects.addPolyline(Polyline(history))
                    newPolyline.setStrokeColor(state.color)
                    newPolyline.setStrokeWidth(3f)
                    polylines[serverId] = newPolyline
                } else {
                    if (polyline.geometry.points.lastOrNull() != history.lastOrNull()) {
                        polyline.geometry = Polyline(history)
                    }
                }
            }

            if (state.shouldCenter) {
                map.move(CameraPosition(point, 13.0f, 0.0f, 0.0f))
            }
        }

        if (!hasCenteredOnAnyServer && validStates.isNotEmpty()) {
            val points = validStates.values.map { it.latestPoint }
            if (points.size == 1) {
                map.move(CameraPosition(points.first(), 13.0f, 0.0f, 0.0f))
            } else {
                val bounds = points.fold(null as com.yandex.mapkit.geometry.BoundingBox?) { box, p ->
                    if (box == null) com.yandex.mapkit.geometry.BoundingBox(p, p) else
                        com.yandex.mapkit.geometry.BoundingBox(
                            Point(minOf(box.southWest.latitude, p.latitude), minOf(box.southWest.longitude, p.longitude)),
                            Point(maxOf(box.northEast.latitude, p.latitude), maxOf(box.northEast.longitude, p.longitude))
                        )
                }
                bounds?.let { map.move(map.cameraPosition(it)) }
            }
            hasCenteredOnAnyServer = true
        }

        Log.d("MAP", "✏️ drawAll() optimized for ${validStates.size} servers")
    }


    private fun createMarkerBitmap(serverId: String, name: String, iconUri: String?): Bitmap {
        val view = layoutInflater.inflate(R.layout.view_marker, null)
        val textView = view.findViewById<TextView>(R.id.marker_title)
        val imageView = view.findViewById<ImageView>(R.id.marker_icon)
        textView.text = name

        if (iconUri != null) {
            try {
                val inputStream = context?.contentResolver?.openInputStream(Uri.parse(iconUri))
                val bitmap = BitmapFactory.decodeStream(inputStream)
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
                if (isAdded) {
                    val lat = from.latitude + (to.latitude - from.latitude) * i / steps
                    val lon = from.longitude + (to.longitude - from.longitude) * i / steps
                    marker.geometry = Point(lat, lon)
                }
            }, i * delay)
        }
    }

    private fun showDetails(serverId: String) {
        val name = viewModel.serverNameMap.value?.get(serverId) ?: "Без имени"
        val iconUri = viewModel.getIconUriForServer(serverId)
        val state = viewModel.mapStates.value?.get(serverId)
        val point = state?.latestPoint
        val timestamp = state?.timestamp ?: System.currentTimeMillis()

        if (point != null) {
            val sideInfo = requireActivity().findViewById<View?>(R.id.side_info_container)
            if (sideInfo != null) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.side_info_container, MarkerInfoSideFragment.newInstance(
                        serverId = serverId,
                        serverName = name,
                        point = point,
                        timestamp = timestamp,
                        iconUri = iconUri
                    ))
                    .commit()
            } else {
                MarkerInfoBottomSheet.newInstance(
                    serverId = serverId,
                    serverName = name,
                    point = point,
                    timestamp = timestamp,
                    iconUri = iconUri
                ).apply {
                    onDelete = { viewModel.deleteServer(it) }
                }.show(parentFragmentManager, "info")
            }
        } else {
            Toast.makeText(requireContext(), "Нет координат для сервера", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val denied = permissionsResult.filterValues { !it }
        if (denied.isNotEmpty()) {
            Toast.makeText(requireContext(), "Для работы карты и аудио нужны разрешения", Toast.LENGTH_LONG).show()
        }
    }


    companion object {
        private var mapKitInitialized = false
    }
}
