package com.shanchuang.modules.credit.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 绑定 application.yml 的 app.credit.*，套餐由配置驱动，不落库 */
@Component
@ConfigurationProperties(prefix = "app.credit")
public class CreditProperties {

    private int freeGrant = 20;
    private List<Pack> packs = new ArrayList<>();

    public int getFreeGrant() {
        return freeGrant;
    }

    public void setFreeGrant(int freeGrant) {
        this.freeGrant = freeGrant;
    }

    public List<Pack> getPacks() {
        return packs;
    }

    public void setPacks(List<Pack> packs) {
        this.packs = packs == null ? new ArrayList<>() : packs;
    }

    public Optional<Pack> findPack(String packId) {
        if (packId == null) {
            return Optional.empty();
        }
        return packs.stream().filter(p -> packId.equals(p.getPackId())).findFirst();
    }

    public static class Pack {
        private String packId;
        private int priceFen;
        private int base;
        private int bonus;

        public String getPackId() {
            return packId;
        }

        public void setPackId(String packId) {
            this.packId = packId;
        }

        public int getPriceFen() {
            return priceFen;
        }

        public void setPriceFen(int priceFen) {
            this.priceFen = priceFen;
        }

        public int getBase() {
            return base;
        }

        public void setBase(int base) {
            this.base = base;
        }

        public int getBonus() {
            return bonus;
        }

        public void setBonus(int bonus) {
            this.bonus = bonus;
        }

        public int total() {
            return base + bonus;
        }
    }
}
