package com.thaimoney;

import com.thaimoney.core.Denomination;
import net.minecraft.world.item.Item;

/** เงินสด 1 ชนิด ตัวไอเทมแต่ละชนิดลงทะเบียนแยกกัน จึงปลอมด้วย NBT/component ไม่ได้ */
public class CashItem extends Item {
    private final Denomination denomination;

    public CashItem(Properties properties, Denomination denomination) {
        super(properties);
        this.denomination = denomination;
    }

    public Denomination denomination() {
        return denomination;
    }
}
