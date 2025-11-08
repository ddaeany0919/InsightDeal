import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class PriceViewModel : ViewModel() {
    // 가격 데이터 예
    private val _priceData = mutableListOf<Float>()
    val priceData: List<Float> get() = _priceData

    // 가격 데이터 강제 새로고침 함수
    fun refreshPriceData() {
        viewModelScope.launch {
            // 여기에 API 호출이나 데이터 갱신 로직 추가
            fetchPriceData()
        }
    }

    private suspend fun fetchPriceData() {
        // 예: 네트워크 API 호출
        withContext(Dispatchers.IO) {
            // 가격 데이터 갱신
            // _priceData 업데이트
        }
    }
}
