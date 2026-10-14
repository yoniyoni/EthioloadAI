package com.ethioloadai.setting.repository;

import com.ethioloadai.setting.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, Long> {

    /** Find a setting by its unique key. */
    Optional<PlatformSetting> findByKey(String key);

    /** Check if a setting key exists. */
    boolean existsByKey(String key);
}
