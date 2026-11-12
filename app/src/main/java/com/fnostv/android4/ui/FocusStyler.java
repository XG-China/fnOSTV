package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

/**
 * 统一的 TV 焦点样式（玻璃拟态）：
 * - 普通态：半透明渐变玻璃 + 细描边
 * - 聚焦态：蓝调渐变边框（上亮下暗）+ 淡蓝色玻璃填充
 * - 标签（chip）：选中是淡主色玻璃，聚焦再叠一层更亮的边框
 */
public final class FocusStyler {
    private FocusStyler() {
    }

    public static void applyCard(final View view) {
        apply(view, 12);
    }

    public static void applyButton(final View view) {
        apply(view, 10);
    }

    /** 统一的可聚焦容器：玻璃底 + 聚焦玻璃边框。 */
    public static void apply(final View view, final int radiusDp) {
        view.setFocusable(true);
        final Context context = view.getContext();
        view.setBackgroundDrawable(FnosTheme.glass(context, radiusDp));
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(hasFocus
                        ? FnosTheme.glassFocused(v.getContext(), radiusDp)
                        : FnosTheme.glass(v.getContext(), radiusDp));
            }
        });
    }

    public static void apply(final View view, final int normalColor, final int focusedColor, final int radiusDp) {
        apply(view, radiusDp);
    }

    /** 输入框：未聚焦淡玻璃，聚焦主色渐变描边。 */
    public static void applyField(final View view) {
        final int radius = 10;
        final Context context = view.getContext();
        view.setBackgroundDrawable(new GlassDrawable(context, 0x14FFFFFF, 0x08FFFFFF,
                0x33FFFFFF, 0x1AFFFFFF, radius, 1));
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Context c = v.getContext();
                v.setBackgroundDrawable(hasFocus
                        ? FnosTheme.glassFocused(c, radius)
                        : new GlassDrawable(c, 0x14FFFFFF, 0x08FFFFFF, 0x33FFFFFF, 0x1AFFFFFF, radius, 1));
            }
        });
    }

    /** 复选框：聚焦时整行加渐变描边，文字提亮。 */
    public static void applyCheck(final CheckBox box) {
        final Context context = box.getContext();
        final int radius = 10;
        box.setBackgroundDrawable(FnosTheme.rounded(0x00000000, radius, context));
        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Context c = v.getContext();
                v.setBackgroundDrawable(hasFocus
                        ? FnosTheme.glassOutline(c, radius, 2)
                        : FnosTheme.rounded(0x00000000, radius, c));
                if (v instanceof CheckBox) {
                    ((CheckBox) v).setTextColor(hasFocus ? FnosTheme.COLOR_TEXT : FnosTheme.COLOR_TEXT_80);
                }
            }
        });
    }

    /** 工具条 chip：聚焦只加渐变边框，不改变选中语义。 */
    public static void applyChip(final TextView chip) {
        chip.setFocusable(true);
        chip.setClickable(true);
        paintChip(chip);
        chip.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                paintChip((TextView) v);
            }
        });
    }

    /** 设置 chip 的“已选中”状态；聚焦样式与选中样式可同时存在。 */
    public static void setChipSelected(TextView chip, boolean selected) {
        chip.setSelected(selected);
        paintChip(chip);
    }

    /** 自绘焦点样式的控件复用。 */
    public static Drawable glowBackground(Context context, int fillColor, int radiusDp) {
        return FnosTheme.glassFocused(context, radiusDp);
    }

    private static void paintChip(TextView chip) {
        boolean selected = chip.isSelected();
        boolean focused = chip.isFocused();
        Context c = chip.getContext();
        Drawable background;
        if (focused) {
            background = selected
                    ? new GlassDrawable(c, 0x552F7CFF, 0x222F7CFF, 0xFFB4D6FF, 0xBB4C92FF, 999, 2)
                    : FnosTheme.glassFocused(c, 999);
        } else if (selected) {
            background = new GlassDrawable(c, 0x332F7CFF, 0x142F7CFF, 0x553C82FF, 0x332F7CFF, 999, 1);
        } else {
            background = FnosTheme.glass(c, 999);
        }
        chip.setBackgroundDrawable(background);
        chip.setTextColor(selected || focused ? FnosTheme.COLOR_TEXT : FnosTheme.COLOR_TEXT_60);
    }
}
