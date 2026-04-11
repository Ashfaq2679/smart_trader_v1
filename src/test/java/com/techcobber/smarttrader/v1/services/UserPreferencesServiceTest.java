package com.techcobber.smarttrader.v1.services;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.techcobber.smarttrader.v1.models.UserPreferences;
import com.techcobber.smarttrader.v1.repositories.UserPreferencesRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserPreferencesService}.
 * Uses Mockito — no database required.
 */
@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

	@Mock
	private UserPreferencesRepository preferencesRepository;

	@InjectMocks
	private UserPreferencesService preferencesService;

	private static UserPreferences samplePrefs(String userId) {
		UserPreferences prefs = new UserPreferences();
		prefs.setUserId(userId);
		prefs.setStrategy("price_action");
		prefs.setGranularity("ONE_HOUR");
		prefs.setBaseAsset("BTC");
		prefs.setQuoteAsset("USDC");
		prefs.setPositionSize("0.05");
		prefs.setMaxDailyLoss("500");
		prefs.setTimezone("UTC");
		prefs.setEnabled(true);
		return prefs;
	}

	// =======================================================================
	// getPreferences
	// =======================================================================

	@Nested
	@DisplayName("getPreferences")
	class GetPreferencesTests {

		@Test
		@DisplayName("Returns preferences when found")
		void returnsPrefsWhenFound() {
			UserPreferences prefs = samplePrefs("user-1");
			when(preferencesRepository.findByUserId("user-1")).thenReturn(Optional.of(prefs));

			Optional<UserPreferences> result = preferencesService.getPreferences("user-1");

			assertThat(result).isPresent();
			assertThat(result.get().getStrategy()).isEqualTo("price_action");
		}

		@Test
		@DisplayName("Returns empty when not found")
		void returnsEmptyWhenNotFound() {
			when(preferencesRepository.findByUserId("unknown")).thenReturn(Optional.empty());

			Optional<UserPreferences> result = preferencesService.getPreferences("unknown");

			assertThat(result).isEmpty();
		}
	}

	// =======================================================================
	// savePreferences
	// =======================================================================

	@Nested
	@DisplayName("savePreferences")
	class SavePreferencesTests {

		@Test
		@DisplayName("Creates new preferences when none exist")
		void createsNewPreferences() {
			when(preferencesRepository.findByUserId("user-1")).thenReturn(Optional.empty());
			when(preferencesRepository.save(any(UserPreferences.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			UserPreferences updates = new UserPreferences();
			updates.setStrategy("momentum");
			updates.setGranularity("FIVE_MINUTE");
			updates.setEnabled(true);

			UserPreferences result = preferencesService.savePreferences("user-1", updates);

			assertThat(result.getUserId()).isEqualTo("user-1");
			assertThat(result.getStrategy()).isEqualTo("momentum");
			assertThat(result.getGranularity()).isEqualTo("FIVE_MINUTE");
			assertThat(result.getUpdatedAt()).isNotNull();
			verify(preferencesRepository).save(any(UserPreferences.class));
		}

		@Test
		@DisplayName("Updates existing preferences")
		void updatesExistingPreferences() {
			UserPreferences existing = samplePrefs("user-1");
			existing.setUpdatedAt(Instant.now().minusSeconds(3600));
			when(preferencesRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));
			when(preferencesRepository.save(any(UserPreferences.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			UserPreferences updates = new UserPreferences();
			updates.setStrategy("scalping");
			updates.setEnabled(false);

			UserPreferences result = preferencesService.savePreferences("user-1", updates);

			assertThat(result.getStrategy()).isEqualTo("scalping");
			assertThat(result.isEnabled()).isFalse();
			// Unchanged fields preserved
			assertThat(result.getGranularity()).isEqualTo("ONE_HOUR");
			assertThat(result.getBaseAsset()).isEqualTo("BTC");
		}

		@Test
		@DisplayName("Preserves existing fields when updates are null")
		void preservesFieldsWhenNull() {
			UserPreferences existing = samplePrefs("user-1");
			when(preferencesRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));
			when(preferencesRepository.save(any(UserPreferences.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			UserPreferences updates = new UserPreferences();
			// All fields null except enabled (primitive boolean defaults to false)

			UserPreferences result = preferencesService.savePreferences("user-1", updates);

			assertThat(result.getStrategy()).isEqualTo("price_action");
			assertThat(result.getGranularity()).isEqualTo("ONE_HOUR");
			assertThat(result.getBaseAsset()).isEqualTo("BTC");
			assertThat(result.getQuoteAsset()).isEqualTo("USDC");
		}

		@Test
		@DisplayName("Sets updatedAt timestamp on save")
		void setsUpdatedAt() {
			when(preferencesRepository.findByUserId("user-1")).thenReturn(Optional.empty());
			when(preferencesRepository.save(any(UserPreferences.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			Instant before = Instant.now();
			UserPreferences updates = new UserPreferences();
			updates.setStrategy("test");
			UserPreferences result = preferencesService.savePreferences("user-1", updates);

			assertThat(result.getUpdatedAt()).isAfterOrEqualTo(before);
		}
	}

	// =======================================================================
	// deletePreferences
	// =======================================================================

	@Nested
	@DisplayName("deletePreferences")
	class DeletePreferencesTests {

		@Test
		@DisplayName("Deletes when preferences exist")
		void deletesWhenExists() {
			when(preferencesRepository.existsByUserId("user-1")).thenReturn(true);

			preferencesService.deletePreferences("user-1");

			verify(preferencesRepository).deleteByUserId("user-1");
		}

		@Test
		@DisplayName("Throws when no preferences found")
		void throwsWhenNotFound() {
			when(preferencesRepository.existsByUserId("unknown")).thenReturn(false);

			assertThatThrownBy(() -> preferencesService.deletePreferences("unknown"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("No preferences found");
		}
	}
}
