package com.sourceofmystery.recipe.altar;

/**
 * 神秘祭坛配方优先级枚举
 * S+ 最高，D 最低
 */
public enum AltarRecipeTier {
    S_PLUS(9, "S+"),
    S(8, "S"),
    A_PLUS(7, "A+"),
    A(6, "A"),
    B_PLUS(5, "B+"),
    B(4, "B"),
    C_PLUS(3, "C+"),
    C(2, "C"),
    D_PLUS(1, "D+"),
    D(0, "D");

    private final int priority;
    private final String displayName;

    AltarRecipeTier(int priority, String displayName) {
        this.priority = priority;
        this.displayName = displayName;
    }

    public int getPriority() {
        return priority;
    }

    public String getDisplayName() {
        return displayName;
    }
}
