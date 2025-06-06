package ru.wizand.safeorbit.presentation.client.audio

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.domain.usecase.StartAudioStreamUseCase
import ru.wizand.safeorbit.domain.usecase.StopAudioStreamUseCase
import javax.inject.Inject

@HiltViewModel
class AudioStreamViewModel @Inject constructor(
    private val startAudioStreamUseCase: StartAudioStreamUseCase,
    private val stopAudioStreamUseCase: StopAudioStreamUseCase
) : ViewModel() {

    private val _isAudioStreaming = MutableLiveData<Boolean>()
    val isAudioStreaming: LiveData<Boolean> = _isAudioStreaming

    private var currentCode: String? = null

    fun startAudioStream(serverId: String, onStarted: (String) -> Unit) {
        viewModelScope.launch {
            _isAudioStreaming.postValue(true)

            val result = startAudioStreamUseCase(
                serverId = serverId,
                onStarted = { code ->
                    currentCode = code
                    onStarted(code)
                },
                onCompleted = {
                    _isAudioStreaming.postValue(false)
                    currentCode = null
                }
            )

            if (result.isFailure) {
                _isAudioStreaming.postValue(false)
            }
        }
    }

    fun stopAudioStream(serverId: String) {
        val code = currentCode ?: return
        viewModelScope.launch {
            stopAudioStreamUseCase(serverId, code)
            _isAudioStreaming.postValue(false)
            currentCode = null
        }
    }
}
