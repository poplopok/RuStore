package com.example.rustorecatalog.model

enum class AgeRating(val label: String) {
    AGE_0("0+"),
    AGE_6("6+"),
    AGE_8("8+"),
    AGE_12("12+"),
    AGE_16("16+"),
    AGE_18("18+"),
    ;

    companion object {
        fun fromLabel(label: String): AgeRating =
            values().find { it.label == label } ?: AGE_0
    }
}

