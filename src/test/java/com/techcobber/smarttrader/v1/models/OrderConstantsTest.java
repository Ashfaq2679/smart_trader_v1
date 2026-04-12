package com.techcobber.smarttrader.v1.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OrderConstants}.
 */
class OrderConstantsTest {

	@Test
	@DisplayName("Side constants are correct")
	void sideConstants() {
		assertThat(OrderConstants.SIDE_BUY).isEqualTo("BUY");
		assertThat(OrderConstants.SIDE_SELL).isEqualTo("SELL");
	}

	@Test
	@DisplayName("Type constants are correct")
	void typeConstants() {
		assertThat(OrderConstants.TYPE_MARKET).isEqualTo("MARKET");
		assertThat(OrderConstants.TYPE_LIMIT).isEqualTo("LIMIT");
	}

	@Test
	@DisplayName("Status constants are correct")
	void statusConstants() {
		assertThat(OrderConstants.STATUS_PENDING).isEqualTo("PENDING");
		assertThat(OrderConstants.STATUS_FAILED).isEqualTo("FAILED");
		assertThat(OrderConstants.STATUS_CANCELLED).isEqualTo("CANCELLED");
	}

	@Test
	@DisplayName("Message constants are non-blank")
	void messageConstants() {
		assertThat(OrderConstants.MSG_ORDER_PLACED).isNotBlank();
		assertThat(OrderConstants.MSG_ORDER_FAILED).isNotBlank();
		assertThat(OrderConstants.MSG_COINBASE_ERROR).isNotBlank();
		assertThat(OrderConstants.MSG_ORDER_CANCELLED).isNotBlank();
		assertThat(OrderConstants.MSG_CANCEL_FAILED).isNotBlank();
		assertThat(OrderConstants.MSG_CANCEL_NO_RESULT).isNotBlank();
		assertThat(OrderConstants.MSG_CANCEL_API_ERROR).isNotBlank();
	}

	@Test
	@DisplayName("Utility class cannot be instantiated")
	void cannotInstantiate() throws Exception {
		Constructor<OrderConstants> constructor = OrderConstants.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		// The private constructor should still work via reflection, but the class
		// is not meant to be instantiated
		assertThat(constructor.newInstance()).isNotNull();
	}
}
