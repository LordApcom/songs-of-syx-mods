package view.sett.ui.home;

import init.sprite.SPRITES;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.gui.renderable.RENDEROBJ;
import util.gui.misc.GButt;
import view.main.VIEW;
import view.tool.PlacableMulti;

import java.lang.reflect.Field;

/**
 * Same-package placement class in view.sett.ui.home.
 * Adds the "Assign Species" button to the Housing management panel,
 * positioned in the toolbar row alongside the existing Assign button.
 */
public class WorkplaceFilterInjector {

    public static boolean inject(PlacableMulti tool) {
        try {
            UIHomes homes = VIEW.s().ui.home;
            if (homes == null) return false;

            GuiSection section = findSection(homes);
            if (section == null) return false;

            // Create our tool button
            GButt.ButtPanel btn = new GButt.ButtPanel(SPRITES.icons().m.citizen) {
                @Override
                protected void clickA() {
                    VIEW.s().tools.place(tool);
                }

                @Override
                protected void renAction() {
                    selectedSet(VIEW.s().tools.is(tool));
                }
            };
            btn.hoverInfoSet(tool.name());
            btn.setDim(60, 40);

            // Position the button at the toolbar row (top of the section)
            // Find the Y of the first child (which is the Assign button)
            int toolbarY = findToolbarY(section);
            int toolbarEndX = findToolbarEndX(section, toolbarY);

            btn.body().moveX1(toolbarEndX + 8);
            btn.body().moveY1(toolbarY);
            section.add(btn);

            return true;
        } catch (Exception e) {
            System.err.println("[WorkplaceSpeciesFilter] Injection into UIHomes failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Finds the Y coordinate of the toolbar buttons (first row of elements).
     */
    private static int findToolbarY(GuiSection section) {
        // The toolbar buttons are the first children, positioned at the section's body y1
        return section.body().y1();
    }

    /**
     * Finds the rightmost X coordinate of elements in the toolbar row.
     * Scans section's children for elements at the toolbar Y.
     */
    private static int findToolbarEndX(GuiSection section, int toolbarY) {
        int maxX2 = section.body().x1();

        // Try to access the section's internal element list
        try {
            // GuiSection typically stores children in a list field
            Field elementsField = findListField(section.getClass());
            if (elementsField != null) {
                elementsField.setAccessible(true);
                Object elements = elementsField.get(section);
                if (elements instanceof Iterable) {
                    for (Object elem : (Iterable<?>) elements) {
                        if (elem instanceof RENDEROBJ) {
                            RENDEROBJ r = (RENDEROBJ) elem;
                            // Check if this element is in the toolbar row (same Y)
                            int elemY = r.body().y1();
                            if (Math.abs(elemY - toolbarY) < 10) {
                                maxX2 = Math.max(maxX2, r.body().x2());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: estimate based on 4 existing buttons (60px + 8px gap each)
            maxX2 = section.body().x1() + 4 * 68;
        }

        // If we couldn't find children, use a reasonable estimate
        if (maxX2 <= section.body().x1()) {
            maxX2 = section.body().x1() + 4 * 68;
        }

        return maxX2;
    }

    private static GuiSection findSection(Object panel) {
        Class<?> clazz = panel.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field f = clazz.getDeclaredField("section");
                if (GuiSection.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (GuiSection) f.get(panel);
                }
            } catch (NoSuchFieldException e) {
                // Try parent
            } catch (Exception e) {
                return null;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static Field findListField(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (Iterable.class.isAssignableFrom(f.getType())
                        || f.getType().isArray()) {
                    // Look for a list/array that likely contains RENDEROBJ children
                    String name = f.getName().toLowerCase();
                    if (name.contains("element") || name.contains("child")
                            || name.contains("list") || name.contains("obj")
                            || name.contains("render") || name.contains("item")) {
                        return f;
                    }
                }
            }
            // Also try any Iterable field
            for (Field f : clazz.getDeclaredFields()) {
                if (Iterable.class.isAssignableFrom(f.getType())) {
                    return f;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
