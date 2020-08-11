package mchorse.mclib.client.gui.framework.elements.keyframes;

import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.Scale;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.keyframes.Keyframe;
import mchorse.mclib.utils.keyframes.KeyframeChannel;
import mchorse.mclib.utils.keyframes.KeyframeEasing;
import mchorse.mclib.utils.keyframes.KeyframeInterpolation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

/**
 * Graph view
 *
 * This GUI element is responsible for displaying and editing of
 * keyframe channel (keyframes and its bezier handles)
 */
public class GuiGraphView extends GuiKeyframeElement
{
    public GuiSheet sheet = new GuiSheet(IKey.str(""), 0, null);
    
    private Scale scaleY = new Scale(true);

    public GuiGraphView(Minecraft mc, Consumer<Keyframe> callback)
    {
        super(mc, callback);
    }

    public void setChannel(KeyframeChannel channel, int color)
    {
        this.sheet.channel = channel;
        this.sheet.color = color;
        this.resetView();
    }

    public void setColor(int color)
    {
        this.sheet.color = color;
    }

    /* Implementation of setters */

    @Override
    public void setTick(double tick, boolean opposite)
    {
        if (this.isMultipleSelected())
        {
            if (this.which == Selection.KEYFRAME)
            {
                tick = (long) tick;
            }

            this.sheet.setTick(tick - this.which.getX(this.getCurrent()), this.which, opposite);
        }
        else
        {
            this.which.setX(this.getCurrent(), tick, opposite);
        }

        this.sliding = true;
    }

    @Override
    public void setValue(double value, boolean opposite)
    {
        if (this.isMultipleSelected())
        {
            this.sheet.setValue(value - this.which.getY(this.getCurrent()), this.which, opposite);
        }
        else
        {
            this.which.setY(this.getCurrent(), value, opposite);
        }
    }

    @Override
    public void setInterpolation(KeyframeInterpolation interp)
    {
        this.sheet.setInterpolation(interp);
    }

    @Override
    public void setEasing(KeyframeEasing easing)
    {
        this.sheet.setEasing(easing);
    }

    /* Graphing code */

    public int toGraphY(double value)
    {
        return (int) (this.scaleY.to(value)) + this.area.my();
    }

    public double fromGraphY(int mouseY)
    {
        return this.scaleY.from(mouseY - this.area.my());
    }

    @Override
    public void resetView()
    {
        this.scaleX.set(0, 2);
        this.scaleY.set(0, 2);

        KeyframeChannel channel = this.sheet.channel;
        int c = channel.getKeyframes().size();

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        if (c > 1)
        {
            for (Keyframe frame : channel.getKeyframes())
            {
                minX = Math.min(minX, frame.tick);
                minY = Math.min(minY, frame.value);
                maxX = Math.max(maxX, frame.tick);
                maxY = Math.max(maxY, frame.value);
            }
        }
        else
        {
            minX = 0;
            maxX = this.duration;
            minY = -10;
            maxY = 10;

            if (c == 1)
            {
                Keyframe first = channel.get(0);

                minX = Math.min(0, first.tick);
                maxX = Math.max(this.duration, first.tick);
                minY = maxY = first.value;
            }
        }

        if (Math.abs(maxY - minY) < 0.01F)
        {
            /* Centerize */
            this.scaleY.shift = minY;
        }
        else
        {
            /* Spread apart vertically */
            this.scaleY.view(minY, maxY, this.area.h, 20);
        }

        /* Spread apart horizontally */
        this.scaleX.view(minX, maxX, this.area.w, 20);
        this.recalcMultipliers();
    }

    @Override
    public Keyframe getCurrent()
    {
        return this.sheet.getKeyframe();
    }

    @Override
    public int getSelectedCount()
    {
        return this.sheet.getSelectedCount();
    }

    @Override
    public void clearSelection()
    {
        this.which = Selection.NOT_SELECTED;
        this.sheet.selected.clear();
    }

    @Override
    public void addCurrent(int mouseX, int mouseY)
    {
        long tick = (long) this.fromGraphX(mouseX);
        double value = this.fromGraphY(mouseY);

        KeyframeEasing easing = KeyframeEasing.IN;
        KeyframeInterpolation interp = KeyframeInterpolation.LINEAR;
        Keyframe frame = this.getCurrent();
        long oldTick = tick;

        if (frame != null)
        {
            easing = frame.easing;
            interp = frame.interp;
            oldTick = frame.tick;
        }

        this.sheet.selected.clear();
        this.sheet.selected.add(this.sheet.channel.insert(tick, value));

        if (oldTick != tick)
        {
            frame = this.getCurrent();
            frame.setEasing(easing);
            frame.setInterpolation(interp);
        }
    }

    @Override
    public void removeCurrent()
    {
        Keyframe frame = this.getCurrent();

        if (frame == null)
        {
            return;
        }

        this.sheet.channel.remove(this.sheet.selected.get(0));
        this.sheet.clearSelection();
        this.which = Selection.NOT_SELECTED;
    }

    @Override
    public void removeSelectedKeyframes()
    {
        this.sheet.removeSelectedKeyframes();
        this.setKeyframe(null);
        this.which = Selection.NOT_SELECTED;
    }

    /**
     * Recalculate grid's multipliers
     */
    @Override
    protected void recalcMultipliers()
    {
        super.recalcMultipliers();

        this.scaleY.mult = this.recalcMultiplier(this.scaleY.zoom);
    }

    /**
     * Make current keyframe by given duration
     */
    public void selectByDuration(long duration)
    {
        if (this.sheet.channel == null)
        {
            return;
        }

        int i = 0;
        this.sheet.selected.clear();

        for (Keyframe frame : this.sheet.channel.getKeyframes())
        {
            if (frame.tick >= duration)
            {
                this.sheet.selected.add(i);

                break;
            }

            i++;
        }

        this.setKeyframe(this.getCurrent());
    }

    /* Mouse input handling */

    @Override
    protected void duplicateKeyframe(Keyframe frame, GuiContext context, int mouseX, int mouseY)
    {
        long offset = (long) this.fromGraphX(mouseX);
        KeyframeChannel channel = this.sheet.channel;
        Keyframe created = channel.get(channel.insert(offset, frame.value));

        this.sheet.selected.clear();
        this.sheet.selected.add(channel.getKeyframes().indexOf(created));
        created.copy(frame);
        created.tick = offset;
    }

    @Override
    protected boolean pickKeyframe(GuiContext context, int mouseX, int mouseY, boolean shift)
    {
        int index = 0;
        int count = this.sheet.channel.getKeyframes().size();
        Keyframe prev = null;

        for (Keyframe frame : this.sheet.channel.getKeyframes())
        {
            boolean left = prev != null && prev.interp == KeyframeInterpolation.BEZIER && this.isInside(frame.tick - frame.lx, frame.value + frame.ly, mouseX, mouseY);
            boolean right = frame.interp == KeyframeInterpolation.BEZIER && this.isInside(frame.tick + frame.rx, frame.value + frame.ry, mouseX, mouseY) && index != count - 1;
            boolean point = this.isInside(frame.tick, frame.value, mouseX, mouseY);

            if (left || right || point)
            {
                int key = this.sheet.selected.indexOf(index);

                if (!shift && key == -1)
                {
                    this.clearSelection();
                }

                Selection which = left ? Selection.LEFT_HANDLE : (right ? Selection.RIGHT_HANDLE : Selection.KEYFRAME);

                if (!shift || which == this.which)
                {
                    this.which = which;

                    if (shift && this.isMultipleSelected() && key != -1)
                    {
                        this.sheet.selected.remove(key);
                        frame = this.getCurrent();
                    }
                    else if (key == -1)
                    {
                        this.sheet.selected.add(index);
                        frame = this.isMultipleSelected() ? this.getCurrent() : frame;
                    }
                    else
                    {
                        frame = this.getCurrent();
                    }

                    this.setKeyframe(frame);
                }

                if (frame != null)
                {
                    this.lastT = left ? frame.tick - frame.lx : (right ? frame.tick + frame.rx : frame.tick);
                    this.lastV = left ? frame.value + frame.ly : (right ? frame.value + frame.ry : frame.value);
                }

                return true;
            }

            prev = frame;
            index++;
        }

        return false;
    }

    private boolean isInside(double tick, double value, int mouseX, int mouseY)
    {
        int x = this.toGraphX(tick);
        int y = this.toGraphY(value);
        double d = Math.pow(mouseX - x, 2) + Math.pow(mouseY - y, 2);

        return Math.sqrt(d) < 4;
    }

    @Override
    protected void setupScrolling(GuiContext context, int mouseX, int mouseY)
    {
        super.setupScrolling(context, mouseX, mouseY);

        this.lastV = this.scaleY.shift;
    }

    @Override
    protected void zoom(int scroll)
    {
        boolean x = GuiScreen.isShiftKeyDown();
        boolean y = GuiScreen.isCtrlKeyDown();
        boolean none = !x && !y;

        /* Scaling X */
        if (x && !y || none)
        {
            this.scaleX.zoom(Math.copySign(this.getZoomFactor(this.scaleX.zoom), scroll), 0.01F, 50F);
        }

        /* Scaling Y */
        if (y && !x || none)
        {
            this.scaleY.zoom(Math.copySign(this.getZoomFactor(this.scaleY.zoom), scroll), 0.01F, 50F);
        }
    }

    @Override
    protected void postSlideSort(GuiContext context)
    {
        /* Resort after dragging the tick thing */
        this.sheet.sort();
        this.sliding = false;
    }

    @Override
    protected void resetMouseReleased(GuiContext context)
    {
        if (this.isGrabbing())
        {
            /* Multi select */
            Area area = new Area();
            KeyframeChannel channel = this.sheet.channel;

            area.setPoints(this.lastX, this.lastY, context.mouseX, context.mouseY, 3);

            for (int i = 0, c = channel.getKeyframes().size(); i < c; i ++)
            {
                Keyframe keyframe = channel.get(i);

                if (area.isInside(this.toGraphX(keyframe.tick), this.toGraphY(keyframe.value)) && !this.sheet.selected.contains(i))
                {
                    this.sheet.selected.add(i);
                }
            }

            if (!this.sheet.selected.isEmpty())
            {
                this.which = Selection.KEYFRAME;
                this.setKeyframe(this.getCurrent());
            }
        }

        super.resetMouseReleased(context);
    }

    /* Rendering */

    @Override
    protected void drawGrid(GuiContext context)
    {
        super.drawGrid(context);

        /* Draw vertical grid */
        int ty = (int) this.fromGraphY(this.area.ey());
        int by = (int) this.fromGraphY(this.area.y - 12);

        int min = Math.min(ty, by) - 1;
        int max = Math.max(ty, by) + 1;
        int mult = this.scaleY.mult;

        min -= min % mult + mult;
        max -= max % mult - mult;

        for (int j = 0, c = (max - min) / mult; j < c; j++)
        {
            int y = this.toGraphY(min + j * mult);

            if (y > this.area.ey())
            {
                continue;
            }

            Gui.drawRect(this.area.x, y, this.area.ex(), y + 1, 0x44ffffff);
            this.font.drawString(String.valueOf(min + j * mult), this.area.x + 4, y + 4, 0xffffff);
        }
    }

    /**
     * Render the graph
     */
    @Override
    protected void drawGraph(GuiContext context, int mouseX, int mouseY)
    {
        if (this.sheet.channel == null || this.sheet.channel.isEmpty())
        {
            return;
        }

        BufferBuilder vb = Tessellator.getInstance().getBuffer();
        KeyframeChannel channel = this.sheet.channel;

        /* Colorize the graph for given channel */
        COLOR.set(this.sheet.color, false);
        float r = COLOR.r;
        float g = COLOR.g;
        float b = COLOR.b;

        GlStateManager.color(1, 1, 1, 1);

        /* Draw the graph */
        vb.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        int index = 0;
        int count = channel.getKeyframes().size();
        Keyframe prev = null;

        for (Keyframe frame : channel.getKeyframes())
        {
            if (prev != null)
            {
                int px = this.toGraphX(prev.tick);
                int fx = this.toGraphX(frame.tick);

                if (prev.interp == KeyframeInterpolation.LINEAR)
                {
                    vb.pos(px, this.toGraphY(prev.value), 0).color(r, g, b, 1).endVertex();
                    vb.pos(fx, this.toGraphY(frame.value), 0).color(r, g, b, 1).endVertex();
                }
                else
                {
                    for (int i = 0; i < 10; i++)
                    {
                        vb.pos(px + (fx - px) * (i / 10F), this.toGraphY(prev.interpolate(frame, i / 10F)), 0).color(r, g, b, 1).endVertex();
                        vb.pos(px + (fx - px) * ((i + 1) / 10F), this.toGraphY(prev.interpolate(frame, (i + 1) / 10F)), 0).color(r, g, b, 1).endVertex();
                    }
                }

                if (prev.interp == KeyframeInterpolation.BEZIER)
                {
                    vb.pos(this.toGraphX(frame.tick - frame.lx), this.toGraphY(frame.value + frame.ly), 0).color(r, g, b, 0.6F).endVertex();
                    vb.pos(this.toGraphX(frame.tick), this.toGraphY(frame.value), 0).color(r, g, b, 0.6F).endVertex();
                }
            }

            if (prev == null)
            {
                vb.pos(0, this.toGraphY(frame.value), 0).color(r, g, b, 1).endVertex();
                vb.pos(this.toGraphX(frame.tick), this.toGraphY(frame.value), 0).color(r, g, b, 1).endVertex();
            }

            if (frame.interp == KeyframeInterpolation.BEZIER && index != count - 1)
            {
                vb.pos(this.toGraphX(frame.tick), this.toGraphY(frame.value), 0).color(r, g, b, 0.6F).endVertex();
                vb.pos(this.toGraphX(frame.tick + frame.rx), this.toGraphY(frame.value + frame.ry), 0).color(r, g, b, 0.6F).endVertex();
            }

            prev = frame;
            index++;
        }

        vb.pos(this.toGraphX(prev.tick), this.toGraphY(prev.value), 0).color(r, g, b, 1).endVertex();
        vb.pos(this.area.ex(), this.toGraphY(prev.value), 0).color(r, g, b, 1).endVertex();

        Tessellator.getInstance().draw();

        /* Draw points */
        vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        index = 0;
        prev = null;

        for (Keyframe frame : channel.getKeyframes())
        {
            this.drawRect(vb, this.toGraphX(frame.tick), this.toGraphY(frame.value), 3, 0xffffff);

            if (frame.interp == KeyframeInterpolation.BEZIER && index != count - 1)
            {
                this.drawRect(vb, this.toGraphX(frame.tick + frame.rx), this.toGraphY(frame.value + frame.ry), 3, 0xffffff);
            }

            if (prev != null && prev.interp == KeyframeInterpolation.BEZIER)
            {
                this.drawRect(vb, this.toGraphX(frame.tick - frame.lx), this.toGraphY(frame.value + frame.ly), 3, 0xffffff);
            }

            prev = frame;
            index++;
        }

        index = 0;
        prev = null;

        for (Keyframe frame : channel.getKeyframes())
        {
            boolean has = this.sheet.selected.contains(index);

            this.drawRect(vb, this.toGraphX(frame.tick), this.toGraphY(frame.value), 2, has && this.which == Selection.KEYFRAME ? 0x0080ff : 0);

            if (frame.interp == KeyframeInterpolation.BEZIER && index != count - 1)
            {
                this.drawRect(vb, this.toGraphX(frame.tick + frame.rx), this.toGraphY(frame.value + frame.ry), 2, has && this.which == Selection.RIGHT_HANDLE ? 0x0080ff : 0);
            }

            if (prev != null && prev.interp == KeyframeInterpolation.BEZIER)
            {
                this.drawRect(vb, this.toGraphX(frame.tick - frame.lx), this.toGraphY(frame.value + frame.ly), 2, has && this.which == Selection.LEFT_HANDLE ? 0x0080ff : 0);
            }

            prev = frame;
            index++;
        }

        Tessellator.getInstance().draw();
    }

    /* Handling dragging */

    @Override
    protected void scrolling(int mouseX, int mouseY)
    {
        super.scrolling(mouseX, mouseY);

        this.scaleY.shift = (mouseY - this.lastY) / this.scaleY.zoom + this.lastV;
    }

    @Override
    protected Keyframe moving(GuiContext context, int mouseX, int mouseY)
    {
        Keyframe frame = this.getCurrent();
        double x = this.fromGraphX(mouseX);
        double y = this.fromGraphY(mouseY);

        if (this.which == Selection.NOT_SELECTED)
        {
            this.moveNoKeyframe(context, frame, x, y);
        }
        else
        {
            if (this.isMultipleSelected())
            {
                int dx = mouseX - this.lastX;
                int dy = mouseY - this.lastY;

                int xx = this.toGraphX(this.lastT);
                int yy = this.toGraphY(this.lastV);

                x = this.fromGraphX(xx + dx);
                y = this.fromGraphY(yy + dy);
            }

            if (GuiScreen.isShiftKeyDown()) x = this.lastT;
            if (GuiScreen.isCtrlKeyDown()) y = this.lastV;

            if (this.which == Selection.LEFT_HANDLE)
            {
                x = -(x - frame.tick);
                y = y - frame.value;
            }
            else if (this.which == Selection.RIGHT_HANDLE)
            {
                x = x - frame.tick;
                y = y - frame.value;
            }

            this.setTick(x, !GuiScreen.isAltKeyDown());
            this.setValue(y, !GuiScreen.isAltKeyDown());
        }

        return frame;
    }
}