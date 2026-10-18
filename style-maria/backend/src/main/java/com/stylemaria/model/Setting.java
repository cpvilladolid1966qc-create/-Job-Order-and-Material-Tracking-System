package com.stylemaria.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "settings")
public class Setting {
    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", length = 1000)
    private String value;
}
