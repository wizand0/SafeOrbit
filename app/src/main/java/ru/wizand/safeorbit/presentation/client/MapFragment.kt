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
import android.view.*
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
import com.yandex.runtime.image.ImageProvider
import ru.wizand.safeorbit.BuildConfig
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.FragmentMapBinding
import ru.wizand.safeorbit.databinding.ViewMarkerBinding

class MapFragment : Fragment() {

    private val mapViewModel: ServerMapViewModel by activityViewModels()

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        MapKitFactory.getInstance().onStart()
        binding.mapView.onStart()
        checkAndRequestPermissionsIfNeeded()
        observeViewModel()
        binding.loadingLayout.visibility = View.VISIBLE
        if (!serversLoaded) {
            viewModel.loadAndObserveServers()
            serversLoaded = true
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onStop()
        MapKitFactory.getInstance().onStop()
        placemarks.clear()
        polylines.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun checkAndRequestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isEmpty()) return

        if (android.Manifest.permission.ACCESS_FINE_LOCATION in notGranted) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.access_to_geo))
                .setMessage(getString(R.string.location_permission_recuired))
                .setPositiveButton("Продолжить") { _, _ ->
                    if (android.Manifest.permission.RECORD_AUDIO in notGranted) {
                        showAudioPermissionDialog(notGranted)
                    } else {
                        permissionRequestLauncher.launch(notGranted.toTypedArray())
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else if (android.Manifest.permission.RECORD_AUDIO in notGranted) {
            showAudioPermissionDialog(notGranted)
        }
    }

    private fun showAudioPermissionDialog(notGranted: List<String>) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.acess_to_microphone))
            .setMessage(getString(R.string.need_permission_for_audio))
            .setPositiveButton("Разрешить") { _, _ ->
                permissionRequestLauncher.launch(notGranted.toTypedArray())
            }
            .setNegativeButton((getString(R.string.cancel)), null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.isConnected.observe(viewLifecycleOwner) { connected ->
            isConnected = connected
            if (!connected && !hasShownConnectionToast) {
                handler.postDelayed({
                    if (!isConnected && isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.no_connection_to_firebase),
                            Toast.LENGTH_LONG
                        ).show()
                        hasShownConnectionToast = true
                    }
                }, 2000)
            } else if (connected) {
                maybeDraw()
            }
        }

        viewModel.serverNameMap.observe(viewLifecycleOwner) {
            cachedNames = it
            val ids = it.keys.toList()
            mapViewModel.observeServerLocations(ids) // ✅ ВАЖНО
            maybeDraw()
        }

        mapViewModel.mapStates.observe(viewLifecycleOwner) {
            cachedStates = it
            maybeDraw()
        }
    }

    private fun maybeDraw() {
        val states = cachedStates
        val names = cachedNames
        if (states != null && names != null && states.isNotEmpty()) {
            val valid = states.keys.filterNot { it == "testServer" }.any { names.containsKey(it) }
            if (valid) {
                drawAll(states, names)
                binding.loadingLayout.visibility = View.GONE
                return
            }
        }

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            if (binding.loadingLayout.visibility == View.VISIBLE) {
                binding.loadingLayout.visibility = View.GONE
                val msg = if (names.isNullOrEmpty()) getString(R.string.no_saved_servers)
                else getString(R.string.problem_with_downloading_from_server)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }, 4000)
    }

    private fun drawAll(states: Map<String, ServerMapState>, nameMap: Map<String, String>) {
        val map = binding.mapView.mapWindow.map
        val validStates = states.filter { (id, _) -> nameMap[id]?.isNotBlank() == true }

        val toRemove = placemarks.keys - validStates.keys
        toRemove.forEach { id ->
            placemarks.remove(id)?.let { map.mapObjects.remove(it) }
            polylines.remove(id)?.let { map.mapObjects.remove(it) }
        }

        for ((serverId, state) in validStates) {
            val name = nameMap[serverId] ?: continue
            val point = state.latestPoint
            val iconUri = viewModel.getIconUriForServer(serverId)
            val bitmap = createMarkerBitmap(serverId, name, iconUri)

            val existingPlacemark = placemarks[serverId]
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
                val bounds =
                    points.fold(null as com.yandex.mapkit.geometry.BoundingBox?) { box, p ->
                        if (box == null) com.yandex.mapkit.geometry.BoundingBox(p, p) else
                            com.yandex.mapkit.geometry.BoundingBox(
                                Point(
                                    minOf(box.southWest.latitude, p.latitude),
                                    minOf(box.southWest.longitude, p.longitude)
                                ),
                                Point(
                                    maxOf(box.northEast.latitude, p.latitude),
                                    maxOf(box.northEast.longitude, p.longitude)
                                )
                            )
                    }
                bounds?.let { map.move(map.cameraPosition(it)) }
            }
            hasCenteredOnAnyServer = true
        }
    }

    private fun createMarkerBitmap(serverId: String, name: String, iconUri: String?): Bitmap {
        val bindingMarker = ViewMarkerBinding.inflate(LayoutInflater.from(context))

        bindingMarker.markerTitle.text = name

        if (!iconUri.isNullOrEmpty()) {
            try {
                val inputStream = context?.contentResolver?.openInputStream(Uri.parse(iconUri))
                val bitmap = BitmapFactory.decodeStream(inputStream)
                bindingMarker.markerIcon.setImageBitmap(bitmap)
            } catch (e: Exception) {
                bindingMarker.markerIcon.setImageResource(R.drawable.red_dot)
            }
        } else {
            bindingMarker.markerIcon.setImageResource(R.drawable.red_dot)
        }

        if (serverId == activeMarkerId) {
            bindingMarker.root.setBackgroundResource(R.drawable.marker_background)
            bindingMarker.root.scaleX = 1.2f
            bindingMarker.root.scaleY = 1.2f
        } else {
            bindingMarker.root.scaleX = 1.0f
            bindingMarker.root.scaleY = 1.0f
        }

        val view = bindingMarker.root
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val output =
            Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
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
//        val state = viewModel.mapStates.value?.get(serverId)
        val state = mapViewModel.mapStates.value?.get(serverId)
        val point = state?.latestPoint
        val timestamp = state?.timestamp ?: System.currentTimeMillis()
        if (point != null) {
            val sideInfo = requireActivity().findViewById<View?>(R.id.side_info_container)
            if (sideInfo != null) {
                parentFragmentManager.beginTransaction()
                    .replace(
                        R.id.side_info_container, MarkerInfoSideFragment.newInstance(
                            serverId = serverId,
                            serverName = name,
                            point = point,
                            timestamp = timestamp,
                            iconUri = iconUri
                        )
                    )
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
            Toast.makeText(requireContext(),
                getString(R.string.no_coorinates_for_server), Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val denied = permissionsResult.filterValues { !it }
        if (denied.isNotEmpty()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.need_permissions_for_map_and_audio),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private var mapKitInitialized = false
    }
}
