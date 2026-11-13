package com.battleship.frontend.ui;

import com.battleship.shared.game.SkillType;
import com.jme3.math.FastMath;

public final class SkillIcons {
    private SkillIcons() { }

    public static void draw(NavalHudSkin skin, SkillType skill, float x, float y, int color) {
        switch (skill) {
            case SONAR -> {
                for (int i = 0; i < 3; i++) skin.arc("Sonar rings", x, y, 6 + i * 8, 2, .25f, 5.5f, color);
                skin.line(x, y, x + 18, y + 18, color, 3);
                skin.arc("Sonar contact", x - 12, y + 13, 3, 6, 0, FastMath.TWO_PI, color);
            }
            case MINE -> {
                skin.arc("Mine body", x, y, 9, 18, 0, FastMath.TWO_PI, color);
                for (int i = 0; i < 8; i++) {
                    float angle = i * FastMath.QUARTER_PI;
                    skin.line(x + FastMath.cos(angle) * 12, y + FastMath.sin(angle) * 12,
                            x + FastMath.cos(angle) * 25, y + FastMath.sin(angle) * 25, color, 5);
                }
                skin.arc("Mine center", x, y, 5, 2, 0, FastMath.TWO_PI, 0x26445C);
            }
            case SHIELD -> {
                float cy=y-3;
                skin.polygon("Aegis heraldic rim", new float[] {x,cy-25,x+18,cy-10,x+21,cy+15,
                        x,cy+22,x-21,cy+15,x-18,cy-10},color,1);
                skin.polygon("Aegis enamel inset", new float[] {x,cy-20,x+14,cy-8,x+16,cy+12,
                        x,cy+17,x-16,cy+12,x-14,cy-8},0x26445C,1).move(0,0,.1f);
                skin.polygon("Aegis celestial crest",new float[]{x,cy-12,x+4,cy,x,cy+11,x-4,cy},color,1).move(0,0,.2f);
                skin.polygon("Aegis crest rays",new float[]{x-10,cy,x,cy-3,x+10,cy,x,cy+3},color,1).move(0,0,.2f);
            }
            case AIRSTRIKE -> {
                skin.polygon("Aircraft fuselage", new float[] {x-3,y-24,x+3,y-24,x+5,y+10,x,y+22,x-5,y+10}, color, 1);
                skin.polygon("Aircraft port swept wing", new float[] {x-27,y-12,x-4,y-7,x-4,y+10,x-23,y+4},color,1);
                skin.polygon("Aircraft starboard swept wing", new float[] {x+4,y-7,x+27,y-12,x+23,y+4,x+4,y+10},color,1);
                skin.polygon("Aircraft tail", new float[] {x-15,y-25,x+15,y-25,x+9,y-18,x-9,y-18}, color, 1);
                for(int side:new int[]{-1,1}) skin.polygon("Aircraft engine nacelle",new float[]{x+side*13-2,y-8,
                        x+side*13+2,y-8,x+side*13+2,y+8,x+side*13-2,y+8},0x26445C,1).move(0,0,.1f);
            }
        }
    }
    public static void bolt(NavalHudSkin skin, float x, float y, float scale, int color) {
        // Two convex pieces keep the lightning's concave silhouette out of triangle-fan fills.
        skin.polygon("Energy lightning", new float[] {x-7*scale,y-scale,x+scale,y-scale,
                x+7*scale,y+12*scale,x-2*scale,y+12*scale}, color, 1);
        skin.polygon("Energy lightning tip", new float[] {x-5*scale,y-12*scale,x+7*scale,y+scale,
                x-scale,y+scale}, color, 1);
    }
}
