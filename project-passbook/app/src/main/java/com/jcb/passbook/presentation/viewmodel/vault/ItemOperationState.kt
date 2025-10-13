package com.jcb.passbook.presentation.viewmodel.vault

sealed class ItemOperationState {
    object Idle : ItemOperationState()
    object Loading : ItemOperationState()
    object Success : ItemOperationState()
    data class Error(val message: String) : ItemOperationState()
}