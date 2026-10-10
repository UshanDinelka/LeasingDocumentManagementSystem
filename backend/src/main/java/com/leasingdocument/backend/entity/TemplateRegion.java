package com.leasingdocument.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "template_region")
public class TemplateRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_region_id")
    private Long templateRegionId;


    @Column(name = "template_id")
    private Long templateId;


    @Column(name = "region_name")
    private String regionName;


    @Column(name = "region_type")
    private String regionType;


    @Column(name = "x")
    private Integer x;


    @Column(name = "y")
    private Integer y;


    @Column(name = "width")
    private Integer width;


    @Column(name = "required_flag")
    private Boolean requiredFlag;



    public Long getTemplateRegionId() {
        return templateRegionId;
    }

    public void setTemplateRegionId(Long templateRegionId) {
        this.templateRegionId = templateRegionId;
    }


    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }


    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }


    public String getRegionType() {
        return regionType;
    }

    public void setRegionType(String regionType) {
        this.regionType = regionType;
    }


    public Integer getX() {
        return x;
    }

    public void setX(Integer x) {
        this.x = x;
    }


    public Integer getY() {
        return y;
    }

    public void setY(Integer y) {
        this.y = y;
    }


    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }


    public Boolean getRequiredFlag() {
        return requiredFlag;
    }

    public void setRequiredFlag(Boolean requiredFlag) {
        this.requiredFlag = requiredFlag;
    }
}