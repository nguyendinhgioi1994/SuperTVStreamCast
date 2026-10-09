package com.tuntech.supertvstreamcast.ui.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuntech.monetization.iap.IapManager
import com.tuntech.monetization.iap.IapProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PaywallViewModel(
    private val iapManager: IapManager,
) : ViewModel() {
    val products
        get() = iapManager.products

    val isLoading = iapManager.isProductLoading

    private val _selectedProduct = MutableStateFlow<IapProduct?>(null)
    val selectedProduct = _selectedProduct.asStateFlow()

    private var _isLoaded = false


    init {
        viewModelScope.launch {
            isLoading.collect { isLoading ->
                if (isLoading || _isLoaded) {
                    return@collect
                }
                getProducts()
            }
        }
    }

    fun getProducts() {
        viewModelScope.launch {
            if (products.isNotEmpty()) {
                _selectedProduct.update {
                    iapManager.defaultProduct ?: products.firstOrNull()
                }
                _isLoaded = true
                return@launch
            }

            this.launch(Dispatchers.IO) {
                iapManager.getProducts()
                _selectedProduct.update {
                    iapManager.defaultProduct ?: products.firstOrNull()
                }
                _isLoaded = true
            }
        }
    }

    fun selectProduct(product: IapProduct) {
        _selectedProduct.update { product }
    }
}
