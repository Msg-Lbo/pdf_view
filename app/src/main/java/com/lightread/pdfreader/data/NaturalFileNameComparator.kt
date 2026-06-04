package com.lightread.pdfreader.data

object NaturalFileNameComparator : Comparator<String> {
    override fun compare(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            val leftChar = left[leftIndex]
            val rightChar = right[rightIndex]
            if (leftChar.isDigit() && rightChar.isDigit()) {
                val leftStart = leftIndex
                val rightStart = rightIndex
                while (leftIndex < left.length && left[leftIndex].isDigit()) leftIndex++
                while (rightIndex < right.length && right[rightIndex].isDigit()) rightIndex++
                val leftNumber = left.substring(leftStart, leftIndex).trimStart('0')
                val rightNumber = right.substring(rightStart, rightIndex).trimStart('0')
                val lengthCompare = leftNumber.length.compareTo(rightNumber.length)
                if (lengthCompare != 0) return lengthCompare
                val numberCompare = leftNumber.compareTo(rightNumber)
                if (numberCompare != 0) return numberCompare
            } else {
                val charCompare = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
                if (charCompare != 0) return charCompare
                leftIndex++
                rightIndex++
            }
        }
        return left.length.compareTo(right.length)
    }
}
