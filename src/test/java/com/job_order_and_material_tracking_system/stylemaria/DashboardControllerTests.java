package com.job_order_and_material_tracking_system.stylemaria;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DashboardControllerTests {

    private final DashboardController controller = new DashboardController();

    @Test
    void mirrorBuilderRouteRedirectsToTheAvailableCustomerPage() {
        assertEquals("redirect:/customize-order.html", controller.mirrorBuilder());
    }

    @Test
    void paymentRouteRedirectsToTheAvailablePaymentPage() {
        assertEquals("redirect:/payment.html", controller.payment());
    }
}
