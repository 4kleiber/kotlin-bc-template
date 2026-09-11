package com.akl.bctemplate.domain

data class PageRequest(val page: Int, val size: Int)

data class Page<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    val hasNext: Boolean get() = page + 1 < totalPages
    val hasPrevious: Boolean get() = page > 0
}
