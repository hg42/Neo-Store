package com.machiav3lli.fdroid.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.machiav3lli.fdroid.content.Preferences
import com.machiav3lli.fdroid.database.DatabaseX
import com.machiav3lli.fdroid.database.entity.Extras
import com.machiav3lli.fdroid.database.entity.Installed
import com.machiav3lli.fdroid.database.entity.Licenses
import com.machiav3lli.fdroid.database.entity.Product
import com.machiav3lli.fdroid.entity.Request
import com.machiav3lli.fdroid.entity.Section
import com.machiav3lli.fdroid.entity.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
open class DoubleListVM(
    val db: DatabaseX,
    primarySource: Source,
    secondarySource: Source,
) : ViewModel() {
    private val cc = Dispatchers.IO
    private val sortFilter = MutableStateFlow("")

    fun setSortFilter(value: String) {
        viewModelScope.launch { sortFilter.emit(value) }
    }

    fun request(source: Source): Request {
        return when (source) {
            Source.AVAILABLE -> Request.productsAll(Section.All)
            Source.SEARCH    -> Request.productsSearch()
            Source.INSTALLED -> Request.productsInstalled()
            Source.UPDATES   -> Request.productsUpdates()
            Source.UPDATED   -> Request.productsUpdated()
            Source.NEW       -> Request.productsNew()
        }
    }

    val installed = db.getInstalledDao().getAllFlow().mapLatest {
        it.associateBy(Installed::packageName)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyMap()
    )

    private var primaryRequest: StateFlow<Request> = combine(
        sortFilter,
        Preferences.subject.map { Preferences[Preferences.Key.HideNewApps] },
        installed
    ) { _, _, _ ->
        request(primarySource)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = request(primarySource)
    )

    val primaryProducts: StateFlow<List<Product>?> = combine(
        primaryRequest,
        installed,
        db.getProductDao().queryFlowList(primaryRequest.value),
        db.getExtrasDao().getAllFlow(),
    ) { req, _, _, _ ->
        withContext(cc) {
            db.getProductDao().queryObject(req)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = null
    )

    private var secondaryRequest = MutableStateFlow(request(secondarySource))

    val secondaryProducts: StateFlow<List<Product>?> = combine(
        secondaryRequest,
        installed,
        db.getProductDao().queryFlowList(secondaryRequest.value),
        db.getExtrasDao().getAllFlow(),
    ) { req, _, _, _ ->
        if (secondarySource != primarySource) withContext(cc) {
            db.getProductDao().queryObject(req)
        }
        else null
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = null
    )

    val repositories = db.getRepositoryDao().getAllFlow().mapLatest { it }

    val categories = db.getCategoryDao().getAllNamesFlow().mapLatest { it }

    val licenses = db.getProductDao().getAllLicensesFlow().mapLatest {
        it.map(Licenses::licenses).flatten().distinct()
    }

    fun setFavorite(packageName: String, setBoolean: Boolean) {
        viewModelScope.launch {
            saveFavorite(packageName, setBoolean)
        }
    }

    private suspend fun saveFavorite(packageName: String, setBoolean: Boolean) {
        withContext(cc) {
            val oldValue = db.getExtrasDao()[packageName]
            if (oldValue != null) db.getExtrasDao()
                .upsert(oldValue.copy(favorite = setBoolean))
            else db.getExtrasDao()
                .upsert(Extras(packageName, favorite = setBoolean))
        }
    }
}

class LatestVM(db: DatabaseX) : DoubleListVM(db, Source.UPDATED, Source.NEW) {
    class Factory(val db: DatabaseX) :
        ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LatestVM::class.java)) {
                return LatestVM(db) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
