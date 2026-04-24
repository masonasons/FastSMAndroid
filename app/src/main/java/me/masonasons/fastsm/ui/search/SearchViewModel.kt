package me.masonasons.fastsm.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.data.repo.TimelineRepository
import me.masonasons.fastsm.domain.model.SearchResults
import me.masonasons.fastsm.domain.model.TimelineSpec
import me.masonasons.fastsm.platform.PlatformFactory

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val accountRepository: AccountRepository,
    private val timelineRepository: TimelineRepository,
    private val platformFactory: PlatformFactory,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SearchEvent> = _events.asSharedFlow()

    init {
        // Debounced live search — 350ms after typing stops.
        _query
            .debounce(350)
            .distinctUntilChanged()
            .filter { it.trim().length >= 2 }
            .onEach { runSearch(it.trim()) }
            .launchIn(viewModelScope)
    }

    fun onQueryChanged(q: String) {
        _query.value = q
        if (q.trim().length < 2) {
            _results.value = SearchResults()
            _error.value = null
            _loading.value = false
        }
    }

    private suspend fun runSearch(q: String) {
        val account = accountRepository.getActiveAccount() ?: return
        val platform = platformFactory.forAccount(account)
        _loading.value = true
        _error.value = null
        runCatching { platform.search(q) }
            .onSuccess { _results.value = it }
            .onFailure { e -> _error.value = e.message ?: e.javaClass.simpleName }
        _loading.value = false
    }

    fun addHashtagTimeline(tag: String) {
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            timelineRepository.add(account.id, TimelineSpec.Hashtag(tag.removePrefix("#")))
            _events.tryEmit(SearchEvent.AddedHashtagTimeline)
        }
    }
}

sealed interface SearchEvent {
    /** The caller should navigate back to Home — a new tab was added there. */
    data object AddedHashtagTimeline : SearchEvent
}
