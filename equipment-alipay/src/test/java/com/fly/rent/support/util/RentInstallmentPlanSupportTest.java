package com.fly.rent.support.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RentInstallmentPlanSupportTest {

    @Test
    void calculateTotalRentAmountMultipliesDailyPriceByQuantityAndDuration() {
        assertEquals(12_000, RentInstallmentPlanSupport.calculateTotalRentAmount(100, 1, 120));
    }

    @Test
    void calculateTotalRentAmountFallsBackToSingleQuantityAndDuration() {
        assertEquals(100, RentInstallmentPlanSupport.calculateTotalRentAmount(100, 0, 0));
    }
}
