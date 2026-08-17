/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.jpa;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.sql.DataSource;

import jakarta.persistence.spi.PersistenceProvider;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.boot.jpa.scanned.IncludedEntity;
import org.springframework.boot.jpa.scanned.excluded.ExcludedEntity;
import org.springframework.boot.jpa.scanned.excluded.nested.NestedExcludedEntity;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.ManagedClassNameFilter;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitPostProcessor;
import org.springframework.orm.jpa.vendor.AbstractJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link EntityManagerFactoryBuilder}.
 *
 * @author Stephane Nicoll
 */
class EntityManagerFactoryBuilderTests {

	@Test
	void setPersistenceUnitPostProcessorsWhenEmpty() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		PersistenceUnitPostProcessor postProcessor = mock();
		PersistenceUnitPostProcessor postProcessor2 = mock();
		builder.setPersistenceUnitPostProcessors(postProcessor, postProcessor2);
		assertThat(builder).extracting("persistenceUnitPostProcessors")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsExactly(postProcessor, postProcessor2);
	}

	@Test
	void addPersistenceUnitPostProcessorsWhenEmpty() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		PersistenceUnitPostProcessor postProcessor = mock();
		PersistenceUnitPostProcessor postProcessor2 = mock();
		builder.addPersistenceUnitPostProcessors(postProcessor, postProcessor2);
		assertThat(builder).extracting("persistenceUnitPostProcessors")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsExactly(postProcessor, postProcessor2);
	}

	@Test
	void setPersistenceUnitPostProcessorsWhenNotEmpty() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		PersistenceUnitPostProcessor postProcessor = mock();
		builder.addPersistenceUnitPostProcessors(postProcessor);
		PersistenceUnitPostProcessor postProcessor2 = mock();
		PersistenceUnitPostProcessor postProcessor3 = mock();
		builder.setPersistenceUnitPostProcessors(postProcessor2, postProcessor3);
		assertThat(builder).extracting("persistenceUnitPostProcessors")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsExactly(postProcessor2, postProcessor3);
	}

	@Test
	void addPersistenceUnitPostProcessorsWhenNotEmpty() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		PersistenceUnitPostProcessor postProcessor = mock();
		builder.addPersistenceUnitPostProcessors(postProcessor);
		PersistenceUnitPostProcessor postProcessor2 = mock();
		builder.addPersistenceUnitPostProcessors(postProcessor2);
		assertThat(builder).extracting("persistenceUnitPostProcessors")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsExactly(postProcessor, postProcessor2);
	}

	@Test
	void requireBootstrapExecutorWhenExecutorProvidedDoesNotThrow() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		builder.requireBootstrapExecutor(() -> new IllegalStateException("BAD"));
		builder.setBootstrapExecutor(new SimpleAsyncTaskExecutor());
		DataSource dataSource = mock();
		assertThatNoException().isThrownBy(builder.dataSource(dataSource)::build);
	}

	@Test
	void requireBootstrapExecutorWhenFallbackExecutorProvidesExecutorDoesNotThrow() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder(SimpleAsyncTaskExecutor::new);
		builder.requireBootstrapExecutor(() -> new IllegalStateException("BAD"));
		DataSource dataSource = mock();
		assertThatNoException().isThrownBy(builder.dataSource(dataSource)::build);
	}

	@Test
	void requireBootstrapExecutorWhenExecutorAndNoFallbackExecutorThrowsException() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		builder.requireBootstrapExecutor(() -> new IllegalStateException("BAD"));
		DataSource dataSource = mock();
		assertThatIllegalStateException().isThrownBy(builder.dataSource(dataSource)::build).withMessage("BAD");
	}

	@Test
	void requireBootstrapExecutorWhenSupplierReturnsNullExecutorAndNoFallbackExecutorThrowsException() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		builder.requireBootstrapExecutor(() -> null);
		DataSource dataSource = mock();
		assertThatIllegalStateException().isThrownBy(builder.dataSource(dataSource)::build)
			.withMessage("A bootstrap executor is required");
	}

	@Test
	void requireBootstrapExecutorWhenFallbackExecutorSupplierProvidesExecutorDoesNotThrow() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder(SimpleAsyncTaskExecutor::new);
		builder.requireBootstrapExecutor(() -> new IllegalStateException("BAD"));
		DataSource dataSource = mock();
		assertThatNoException().isThrownBy(builder.dataSource(dataSource)::build);
	}

	@Test
	void fallbackExecutorSupplierIsNotInvokedWhenBootstrapExecutorNotRequired() {
		AtomicBoolean invoked = new AtomicBoolean();
		EntityManagerFactoryBuilder builder = createEmptyBuilder(() -> {
			invoked.set(true);
			return new SimpleAsyncTaskExecutor();
		});
		DataSource dataSource = mock();
		builder.dataSource(dataSource).build();
		assertThat(invoked).isFalse();
	}

	@Test
	void excludePackagesWhenEmptyDoesNotConfigureAFilter() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		LocalContainerEntityManagerFactoryBean factory = builder.dataSource(mock())
			.packages(IncludedEntity.class)
			.excludePackages((String[]) null)
			.build();
		assertThat(factory).extracting("internalPersistenceUnitManager").extracting("managedClassNameFilter")
			.isNull();
	}

	@Test
	void excludePackagesFiltersExcludedEntitiesFromScan() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		LocalContainerEntityManagerFactoryBean factory = builder.dataSource(mock())
			.packages("org.springframework.boot.jpa.scanned")
			.excludePackages("org.springframework.boot.jpa.scanned.excluded")
			.build();
		ManagedClassNameFilter filter = extractManagedClassNameFilter(factory);
		assertThat(filter).isNotNull();
		assertThat(scanManagedTypes(filter)).containsExactly(IncludedEntity.class.getName());
	}

	@Test
	void excludePackagesAlsoExcludesSubPackages() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		LocalContainerEntityManagerFactoryBean factory = builder.dataSource(mock())
			.packages("org.springframework.boot.jpa.scanned")
			.excludePackages("org.springframework.boot.jpa.scanned.excluded")
			.build();
		ManagedClassNameFilter filter = extractManagedClassNameFilter(factory);
		assertThat(filter).isNotNull();
		assertThat(filter.matches(ExcludedEntity.class.getName())).isFalse();
		assertThat(filter.matches(NestedExcludedEntity.class.getName())).isFalse();
		assertThat(filter.matches(IncludedEntity.class.getName())).isTrue();
	}

	@Test
	void setManagedClassNameFilterIsAppliedWhenSet() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		ManagedClassNameFilter filter = (className) -> className.startsWith("org.springframework.boot.jpa.scanned");
		builder.setManagedClassNameFilter(filter);
		LocalContainerEntityManagerFactoryBean factory = builder.dataSource(mock())
			.packages("org.springframework.boot.jpa.scanned")
			.build();
		assertThat(extractManagedClassNameFilter(factory)).isSameAs(filter);
	}

	@Test
	void excludePackagesCombinedWithManagedClassNameFilter() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		builder.setManagedClassNameFilter((className) -> className.startsWith("org.springframework.boot.jpa.scanned"));
		LocalContainerEntityManagerFactoryBean factory = builder.dataSource(mock())
			.packages("org.springframework.boot.jpa.scanned")
			.excludePackages("org.springframework.boot.jpa.scanned.excluded")
			.build();
		ManagedClassNameFilter filter = extractManagedClassNameFilter(factory);
		assertThat(filter).isNotNull();
		assertThat(filter.matches(IncludedEntity.class.getName())).isTrue();
		assertThat(filter.matches(ExcludedEntity.class.getName())).isFalse();
		assertThat(filter.matches("com.example.OutsideEntity")).isFalse();
	}

	@Test
	void excludePackagesMultipleValuesExcludesAllOfThem() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		ManagedClassNameFilter filter = extractManagedClassNameFilter(builder.dataSource(mock())
			.packages("org.springframework.boot.jpa.scanned")
			.excludePackages("org.springframework.boot.jpa.scanned.excluded", "org.springframework.boot.jpa.scanned.other")
			.build());
		assertThat(filter.matches(ExcludedEntity.class.getName())).isFalse();
		assertThat(filter.matches(NestedExcludedEntity.class.getName())).isFalse();
		assertThat(filter.matches(IncludedEntity.class.getName())).isTrue();
	}

	@Test
	void excludePackagesByBasePackageClassUsesTheirPackages() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		ManagedClassNameFilter filter = extractManagedClassNameFilter(builder.dataSource(mock())
			.packages("org.springframework.boot.jpa.scanned")
			.excludePackages(ExcludedEntity.class)
			.build());
		assertThat(filter).isNotNull();
		assertThat(filter.matches(ExcludedEntity.class.getName())).isFalse();
		assertThat(filter.matches(IncludedEntity.class.getName())).isTrue();
	}

	@Test
	void excludePackagesWithEmptyArrayDoesNotConfigureAFilter() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		LocalContainerEntityManagerFactoryBean factory = builder.dataSource(mock())
			.packages(IncludedEntity.class)
			.excludePackages(new String[0])
			.build();
		assertThat(extractManagedClassNameFilter(factory)).isNull();
	}

	@Test
	void excludePackagesHasNoEffectWhenManagedTypesIsProvided() {
		EntityManagerFactoryBuilder builder = createEmptyBuilder();
		PersistenceManagedTypes managedTypes = PersistenceManagedTypes.of(IncludedEntity.class.getName());
		LocalContainerEntityManagerFactoryBean factory = builder.dataSource(mock())
			.managedTypes(managedTypes)
			.excludePackages("org.springframework.boot.jpa.scanned.excluded")
			.build();
		Object persistenceUnitManager = ReflectionTestUtils.getField(factory, "internalPersistenceUnitManager");
		assertThat(ReflectionTestUtils.getField(persistenceUnitManager, "managedTypes")).isSameAs(managedTypes);
	}

	private ManagedClassNameFilter extractManagedClassNameFilter(LocalContainerEntityManagerFactoryBean factory) {
		Object persistenceUnitManager = ReflectionTestUtils.getField(factory, "internalPersistenceUnitManager");
		return (ManagedClassNameFilter) ReflectionTestUtils.getField(persistenceUnitManager, "managedClassNameFilter");
	}

	private List<String> scanManagedTypes(ManagedClassNameFilter filter) {
		PersistenceManagedTypes managedTypes = new PersistenceManagedTypesScanner(new DefaultResourceLoader(), filter)
			.scan("org.springframework.boot.jpa.scanned");
		return managedTypes.getManagedClassNames();
	}

	private EntityManagerFactoryBuilder createEmptyBuilder() {
		return createEmptyBuilder(() -> null);
	}

	private EntityManagerFactoryBuilder createEmptyBuilder(
			Supplier<? extends @Nullable AsyncTaskExecutor> fallbackBootstrapExecutorSupplier) {
		Function<DataSource, Map<String, ?>> jpaPropertiesFactory = (dataSource) -> Collections.emptyMap();
		return new EntityManagerFactoryBuilder(new TestJpaVendorAdapter(), jpaPropertiesFactory, null, null,
				fallbackBootstrapExecutorSupplier);
	}

	static class TestJpaVendorAdapter extends AbstractJpaVendorAdapter {

		@Override
		public PersistenceProvider getPersistenceProvider() {
			return mock();
		}

	}

}
