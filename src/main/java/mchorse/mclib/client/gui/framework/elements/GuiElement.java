package mchorse.mclib.client.gui.framework.elements;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.context.GuiContextMenu;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.tooltips.ITooltip;
import mchorse.mclib.client.gui.framework.tooltips.LabelTooltip;
import mchorse.mclib.client.gui.utils.KeybindManager;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.client.gui.utils.resizers.IResizer;
import mchorse.mclib.client.gui.utils.resizers.Flex;
import mchorse.mclib.client.gui.utils.resizers.Margin;
import mchorse.mclib.utils.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@SideOnly(Side.CLIENT)
public class GuiElement extends Gui implements IGuiElement
{
    /**
     * Area of this element (i.e. position and size) 
     */
    public Area area = new Area();

    /**
     * Element's margin (it's used only by layout resizers)
     */
    public final Margin margin = new Margin();

    /**
     * Resizer of this class
     */
    protected IResizer resizer;

    /**
     * Flex resizer of this class
     */
    protected Flex flex;

    /**
     * Tooltip instance
     */
    public ITooltip tooltip;

    /**
     * Keybind manager
     */
    public KeybindManager keybinds;

    /**
     * Context menu supplier
     */
    public Supplier<GuiContextMenu> contextMenu;

    /**
     * Hide tooltip
     */
    public boolean hideTooltip;

    /**
     * Whether this element should be ignored by post resizers
     */
    public boolean ignored;

    /**
     * Whether this element can be culled if it's out of viewport
     */
    public boolean culled = true;

    /**
     * Whether this element is a container
     */
    protected boolean container;

    /**
     * Parent GUI element
     */
    protected GuiElement parent;

    /**
     * Children elements
     */
    private GuiElements<IGuiElement> children;

    /**
     * Whether this element is enabled (can handle any input) 
     */
    protected boolean enabled = true;

    /**
     * Whether this element is visible 
     */
    protected boolean visible = true;

    /* Useful references */
    protected Minecraft mc;
    protected FontRenderer font;

    /**
     * Initiate GUI element with Minecraft's instance 
     */
    public GuiElement(Minecraft mc)
    {
        this.mc = mc;
        this.font = mc.fontRenderer;
    }

    /* Hierarchy management */

    public GuiBase.GuiRootElement getRoot()
    {
        GuiElement element = this;

        while (element.getParent() != null)
        {
            element = element.getParent();
        }

        return element instanceof GuiBase.GuiRootElement ? (GuiBase.GuiRootElement) element : null;
    }

    public GuiElement getParent()
    {
        return this.parent;
    }

    public boolean hasParent()
    {
        return this.parent != null;
    }

    public boolean isDescendant(GuiElement element)
    {
        if (this == element)
        {
            return false;
        }

        while (element != null)
        {
            if (element.parent == this)
            {
                return true;
            }

            element = element.parent;
        }

        return false;
    }

    public List<IGuiElement> getChildren()
    {
        if (this.children == null)
        {
            return Collections.emptyList();
        }

        return this.children.elements;
    }

    public <T> List<T> getChildren(Class<T> clazz)
    {
        return getChildren(clazz, new ArrayList<T>());
    }

    public <T> List<T> getChildren(Class<T> clazz, List<T> list)
    {
        return getChildren(clazz, list, false);
    }

    public <T> List<T> getChildren(Class<T> clazz, List<T> list, boolean includeItself)
    {
        if (includeItself && clazz.isAssignableFrom(this.getClass()))
        {
            list.add(clazz.cast(this));
        }

        for (IGuiElement element : this.getChildren())
        {
            if (clazz.isAssignableFrom(element.getClass()))
            {
                list.add(clazz.cast(element));
            }

            if (element instanceof GuiElement)
            {
                ((GuiElement) element).getChildren(clazz, list, includeItself);
            }
        }

        return list;
    }

    public void prepend(IGuiElement element)
    {
        if (this.children == null)
        {
            this.children = new GuiElements<IGuiElement>(this);
        }

        this.markChild(element);
        this.children.prepend(element);
    }

    public void add(IGuiElement element)
    {
        if (this.children == null)
        {
            this.children = new GuiElements<IGuiElement>(this);
        }

        this.markChild(element);
        this.children.add(element);
    }

    public void add(IGuiElement... elements)
    {
        if (this.children == null)
        {
            this.children = new GuiElements<IGuiElement>(this);
        }

        for (IGuiElement element : elements)
        {
            this.markChild(element);
            this.children.add(element);
        }
    }

    public void addAfter(IGuiElement target, IGuiElement element)
    {
        if (this.children == null)
        {
            return;
        }

        if (this.children.addAfter(target, element))
        {
            this.markChild(element);
        }
    }

    public void addBefore(IGuiElement target, IGuiElement element)
    {
        if (this.children == null)
        {
            return;
        }

        if (this.children.addBefore(target, element))
        {
            this.markChild(element);
        }
    }

    private void markChild(IGuiElement element)
    {
        if (element instanceof GuiElement)
        {
            GuiElement child = (GuiElement) element;

            child.parent = this;

            if (this.resizer != null)
            {
                this.resizer.add(this, child);
            }
        }
    }

    public void removeAll()
    {
        if (this.children == null)
        {
            return;
        }

        for (IGuiElement element : this.children.elements)
        {
            if (element instanceof GuiElement)
            {
                if (this.resizer != null)
                {
                    this.resizer.remove(this, (GuiElement) element);
                }

                ((GuiElement) element).parent = null;
            }
        }

        this.children.clear();
    }

    public void removeFromParent()
    {
        if (this.hasParent())
        {
            this.parent.remove(this);
        }
    }

    public void remove(GuiElement element)
    {
        if (this.children.elements.remove(element))
        {
            if (this.resizer != null)
            {
                this.resizer.remove(this, element);
            }

            element.parent = null;
        }
    }

    /* Setters */

    public GuiElement removeTooltip()
    {
        this.tooltip = null;

        return this;
    }

    public GuiElement tooltip(ITooltip tooltip)
    {
        this.tooltip = tooltip;

        return this;
    }

    public GuiElement tooltip(IKey label)
    {
        this.tooltip = new LabelTooltip(label, Direction.BOTTOM);

        return this;
    }

    public GuiElement tooltip(IKey label, Direction direction)
    {
        this.tooltip = new LabelTooltip(label, direction);

        return this;
    }

    public GuiElement tooltip(IKey label, int width, Direction direction)
    {
        this.tooltip = new LabelTooltip(label, width, direction);

        return this;
    }

    public GuiElement hideTooltip()
    {
        this.hideTooltip = true;

        return this;
    }

    public GuiElement noCulling()
    {
        this.culled = false;

        return this;
    }

    /* Keybind manager */

    public KeybindManager keys()
    {
        if (this.keybinds == null)
        {
            this.keybinds = new KeybindManager();
        }

        return this.keybinds;
    }

    /* Container stuff */

    public GuiElement markContainer()
    {
        this.container = true;

        return this;
    }

    public GuiElement markIgnored()
    {
        this.ignored = true;

        return this;
    }

    public boolean isContainer()
    {
        return this.container;
    }

    public GuiElement getParentContainer()
    {
        GuiElement element = this.getParent();

        while (element != null && !element.isContainer())
        {
            element = element.getParent();
        }

        return element;
    }

    public GuiElement context(Supplier<GuiContextMenu> supplier)
    {
        this.contextMenu = supplier;

        return this;
    }

    /* Resizer methods */

    /**
     * This method assigns a {@link Flex} to this element and sets it as the main resizer.
     *
     * Flex isn't necessary when you place this element into an element with column, row or
     * grid layouts. Use this to avoid manually calculating the position and scale.
     *
     * @return the {@link Flex} object that is assigned to the main resizer.
     */
    public Flex flex()
    {
        if (this.flex == null)
        {
            this.flex = new Flex(this);

            if (this.resizer == null)
            {
                this.resizer = this.flex;
            }
        }

        return this.flex;
    }

    public void flex(Flex flex)
    {
        if (flex != null)
        {
            this.flex = flex;
        }
    }

    public IResizer resizer()
    {
        return this.resizer;
    }

    public GuiElement resizer(IResizer resizer)
    {
        this.resizer = resizer;

        return this;
    }

    /* Margin */

    public GuiElement margin(int all)
    {
        return this.margin(all, all);
    }

    public GuiElement margin(int horizontal, int vertical)
    {
        return this.margin(horizontal, vertical, horizontal, vertical);
    }

    public GuiElement margin(int left, int top, int right, int bottom)
    {
        this.margin.all(left, top, right, bottom);

        return this;
    }

    public GuiElement marginLeft(int left)
    {
        this.margin.left(left);

        return this;
    }

    public GuiElement marginTop(int top)
    {
        this.margin.top(top);

        return this;
    }

    public GuiElement marginRight(int right)
    {
        this.margin.right(right);

        return this;
    }

    public GuiElement marginBottom(int bottom)
    {
        this.margin.bottom(bottom);

        return this;
    }

    /* Enabled methods */

    @Override
    public boolean isEnabled()
    {
        return this.enabled && this.visible;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    @Override
    public boolean isVisible()
    {
        return this.visible;
    }

    public void setVisible(boolean visible)
    {
        this.visible = visible;
    }

    public void toggleVisible()
    {
        this.visible = !this.visible;
    }

    /**
     * Whether element can be seen on the screen
     */
    public boolean canBeSeen()
    {
        if (!this.hasParent() || !this.isVisible())
        {
            return false;
        }

        GuiElement element = this;

        while (true)
        {
            if (!element.isVisible())
            {
                return false;
            }

            GuiElement parent = element.getParent();

            if (parent instanceof GuiDelegateElement && ((GuiDelegateElement) parent).delegate != element)
            {
                return false;
            }

            if (parent == null)
            {
                break;
            }

            element = parent;
        }

        return element instanceof GuiBase.GuiRootElement;
    }

    /* Overriding those methods so it would be much easier to 
     * override only needed methods in subclasses */

    @Override
    public void resize()
    {
        if (this.resizer != null)
        {
            this.resizer.apply(this.area);
        }

        if (this.children != null)
        {
            this.children.resize();
        }

        if (this.resizer != null)
        {
            this.resizer.postApply(this.area);
        }
    }

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        if (this.children != null && this.children.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context) && context.mouseButton == 1)
        {
            if (!context.hasContextMenu())
            {
                GuiContextMenu menu = this.createContextMenu(context);

                if (menu != null)
                {
                    context.setContextMenu(menu);

                    return true;
                }
            }
        }

        return false;
    }

    public void clickItself(GuiContext context)
    {
        this.clickItself(context, 0);
    }

    public void clickItself(GuiContext context, int mouseButton)
    {
        if (!this.isEnabled())
        {
            return;
        }

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;
        int button = context.mouseButton;

        context.mouseX = this.area.x + 1;
        context.mouseY = this.area.y + 1;
        context.mouseButton = mouseButton;

        this.mouseClicked(context);

        context.mouseX = mouseX;
        context.mouseY = mouseY;
        context.mouseButton = button;
    }

    /**
     * Create a context menu instance
     *
     * Some subclasses of GuiElement might want to override this method in order to create their
     * own context menus.
     */
    public GuiContextMenu createContextMenu(GuiContext context)
    {
        return this.contextMenu == null ? null : this.contextMenu.get();
    }

    @Override
    public boolean mouseScrolled(GuiContext context)
    {
        return this.children != null && this.children.mouseScrolled(context);
    }

    @Override
    public void mouseReleased(GuiContext context)
    {
        if (this.children != null)
        {
            this.children.mouseReleased(context);
        }
    }

    @Override
    public boolean keyTyped(GuiContext context)
    {
        if (this.children != null && this.children.keyTyped(context))
        {
            return true;
        }

        if (this.keybinds != null && this.keybinds.check(context, this.area.isInside(context)))
        {
            return true;
        }

        return false;
    }

    @Override
    public boolean canBeDrawn(Area viewport)
    {
        return !this.culled || viewport.intersects(this.area);
    }

    @Override
    public void draw(GuiContext context)
    {
        if (this.keybinds != null && this.isEnabled())
        {
            this.keybinds.add(context, this.area.isInside(context));
        }

        if (this.tooltip != null && this.area.isInside(context))
        {
            context.tooltip.set(context, this);
        }
        else if ((this.hideTooltip || this.container) && this.area.isInside(context))
        {
            context.resetTooltip();
        }

        if (this.children != null)
        {
            this.children.draw(context);
        }
    }

    public void drawTooltip(GuiContext context, Area area)
    {
        context.tooltip.draw(this.tooltip, context);
    }
}