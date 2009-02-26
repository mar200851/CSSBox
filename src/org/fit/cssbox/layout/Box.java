/*
 * Box.java
 * Copyright (c) 2005-2007 Radek Burget
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * Created on 11. z��2005, 18:36
 */

package org.fit.cssbox.layout;

import java.net.URL;
import java.util.*;
import java.awt.*;

import cz.vutbr.web.css.*;

import org.w3c.dom.*;

import org.fit.cssbox.css.CSSUnits;
import org.fit.cssbox.css.DOMAnalyzer;

/**
 * A visual formatting box. It can be of two types: an inline box
 * or a block box.
 *
 * @author  radek
 */
abstract public class Box
{
    protected static final short DRAW_ALL = 0; //drawing stages
    protected static final short DRAW_NONFLOAT = 1;
    protected static final short DRAW_FLOAT = 2;
    protected static final short DRAW_BOTH = 0; //drawing modes
    protected static final short DRAW_FG = 1;
    protected static final short DRAW_BG = 2;
    
    protected static int next_order = 0;
    
    /** Is this box a block? */
    protected boolean isblock;
    
    /** Is this box empty? (doesn't contain any visible non-empty block */
    protected boolean isempty;
    
    /** Is this element displayed? (it has not display: none) */
    protected boolean displayed; 

    /** Is this element visible? (it has not visibility: hidden) */
    protected boolean visible;
        
    /** The DOM node that forms this box. It is either an inline element
     * (e.g. <em>) or a text node (anonymous box) */
    protected Node node;
    
    /** The order of the node in the code (first node is 0) */
    protected int order;
    
    /** The style of the node (for element nodes only) */
    protected NodeData style;
    
    /** Box position on the screen relatively to the containing content box.
     * Coordinates of the whole box including margin. */
    protected Rectangle bounds;
    
    /** Absolute box position (on the whole page) */
    protected Rectangle absbounds;
    
    /** The viewport */
    protected Viewport viewport;
    
    /** Parent box */
    protected ElementBox parent;
    
    /** Containing block */
    protected BlockBox cblock;
    
    /** Clipping box. The box is not visible if it is not inside of the clipping box.
     * Normally, the clipping box is the viewport or the nearest parent with
     * overflow set to hidden */
    protected BlockBox clipblock;
    
    /** Maximal total width for the layout (obtained from the owner box) */
    protected int availwidth;
    
    /** Graphics context */
    protected Graphics2D g;
    
    /** Rendering context (em size etc.) */
    protected VisualContext ctx;
    
    /** Base URL */
    protected URL base;
    
    /** True if this box is a result of splitting */
    protected boolean splitted;
    
    /** Remaining part of the box after splitting */
    protected Box rest;
    
    //==============================================================
    
    /**
     * Create a new instance of a box
     * @param n the DOM node that forms this box
     * @param g current graphics context
     * @param ctx current visual context
     */
    public Box(Node n, Graphics2D g, VisualContext ctx)
    {
        this.g = g;
        this.ctx = ctx;
        node = n;
        isblock = false;
        style = null;
        isempty = true;

        bounds = new Rectangle();
        absbounds = new Rectangle();
        displayed = true;
        visible = true;
        splitted = false;
        rest = null;
    }

    /**
     * Copy all the values from another box
     * @param src source box
     */
    public void copyValues(Box src)
    {
        isblock = src.isblock;
        order = src.order;
        style = src.style;
        isempty = src.isempty;
        availwidth = src.availwidth;
        viewport = src.viewport;
        parent = src.parent;
        cblock = src.cblock;
        clipblock = src.clipblock;

        bounds = new Rectangle(src.bounds);
        absbounds = new Rectangle(src.absbounds);
        displayed = src.displayed;
        visible = src.visible;
        splitted = src.splitted;
        rest = src.rest;
    }
    
    /** 
     * Creates a box tree from a DOM node. Recursively creates the child boxes 
     * from the child nodes. If the element is inline, we try to create
     * an inline box. When a block box occures inside, everything is
     * converted to blocks.
     * @param n DOM tree root node
     * @param g graphics context to render on
     * @param ctx visual context to render on
     * @param decoder a CSS style decoder
     * @param baseurl the base URL of the document
     * @param viewport current viewport
     * @param contbox the containing box of the new box when not absolutley positioned
     * @param absbox the containing box of the new box when absolutley positioned
     * @param clipbox the clipping block of this subtree
     * @return the root node of the created tree of boxes
     */
    public static Box createBoxTree(Node n, Graphics2D g, VisualContext ctx,
                                    DOMAnalyzer decoder, URL baseurl,
                                    Viewport viewport,
                                    BlockBox contbox, BlockBox absbox, BlockBox clipbox,
                                    ElementBox parent)
    {
        //-- Text nodes --
        if (n.getNodeType() == Node.TEXT_NODE)
        {
            TextBox text = new TextBox((Text) n, g, ctx);
            text.setOrder(next_order++);
            text.setContainingBlock(contbox);
            text.setClipBlock(clipbox);
            return text;
        }
        //-- Element nodes --
        else
        {
            //Create the new box
            ElementBox root = createBox((Element) n, g, ctx, decoder, baseurl, viewport, parent, null);
            
            //Determine the containing boxes
            BlockBox newcont = contbox;
            BlockBox newabs = absbox;
            BlockBox newclip = clipbox;
            if (root.isBlock())
            {
                BlockBox block = (BlockBox) root; 
                //Setup my containing box
                if (block.position == BlockBox.POS_ABSOLUTE ||
                    block.position == BlockBox.POS_FIXED)
                    root.setContainingBlock(absbox);
                else    
                    root.setContainingBlock(contbox);
                //A positioned box forms a content box for following absolutely
                //positioned boxes
                if (block.position == BlockBox.POS_ABSOLUTE ||
                    block.position == BlockBox.POS_RELATIVE ||
                    block.position == BlockBox.POS_FIXED)
                    newabs = block;
                //Any block box forms a containing box for not positioned elements
                newcont = block;
                //A box with overflow:hidden creates a clipping box
                if (block.overflow == BlockBox.OVERFLOW_HIDDEN)
                    newclip = block;
            }
            else    
                root.setContainingBlock(contbox);
            
            //Set the clipping block
            root.setClipBlock(clipbox);
            
            //process the subtree
            if (root.isDisplayed())
            {
                NodeList children = n.getChildNodes();
                
                //Check if anonymous inline boxes should be created
                boolean textonly = true;
                for (int i = 0; i < children.getLength(); i++)
                    if (children.item(i).getNodeType() == Node.ELEMENT_NODE)
                        textonly = false;
                
                //a reference box for possible absolutely positioned boxes
                //normally, it is the previous in-flow box, or null, if this is the first box
                Box inflowReference = null;
                
                //Create child boxes
                for (int i = 0; i < children.getLength(); i++)
                {
                    Node cn = children.item(i);
                    
                    if (cn.getNodeType() == Node.ELEMENT_NODE ||
                        cn.getNodeType() == Node.TEXT_NODE)
                    {
                        //Create a new subtree
                        Box newbox = createBoxTree(cn, (Graphics2D) g.create(), ctx.create(), 
                                                    decoder, baseurl, viewport,
                                                    newcont, newabs, newclip, root);
                        //If the new box is block, it's parent box must be block too
                        //This is not true for positioned boxes (they are moved to their containing block)
                        boolean inflow = true;
                        if (newbox.isBlock())
                        {
                            BlockBox newblock = (BlockBox) newbox;
                            //if the box contains in-flow block boxes, it must be a block box
                            //this should happen by error only (a block inside of inline element)
                            if (newblock.isInFlow())
                            {
                                if (!root.isBlock())
                                    root = new BlockBox((InlineBox) root);
                                ((BlockBox) root).contblock = true;
                                updateContainingBoxes(root, contbox, absbox);
                                updateContainingBoxes((ElementBox) newbox, (BlockBox) root, newabs);
                            }
                            else
                                inflow = false;
                        }
                        //Add the new child
                        if (inflow) //box in flow (inline or block) - add it to it's parent
                        {
                            if (root.isDisplayed() && !newbox.isEmpty())
                            	root.isempty = false;
                            if (root.isBlock() && newbox.isInFlow())
                            	((BlockBox) root).anyinflow = true;
                            if (cn.getNodeType() == Node.TEXT_NODE && !textonly)
                            {
                                //create anonymous inline box for the text
                                Element anelem = createAnonymousBox(root.getElement().getOwnerDocument(), "Xspan", "inline");
                                ElementBox anbox = createBox(anelem, g, ctx, decoder, baseurl, viewport, root, "intline");
                                newbox.setParent(anbox);
                                anbox.addSubBox(newbox);
                                anbox.isempty = false;
                                anbox.setParent(root);
                                anbox.setContainingBlock(newcont);
                                anbox.setClipBlock(newclip);
                                root.addSubBox(anbox);
                            }
                            else
                            {
                                //insert directly
                                newbox.setParent(root);
                                root.addSubBox(newbox);
                            }
                            inflowReference = newbox;
                        }
                        else //positioned or floating (block only) - add it to its containing block
                        {
                            BlockBox newcblock = newbox.getContainingBlock();
                            if (newcblock.isDisplayed() && !newbox.isEmpty())
                                newcblock.isempty = false;
                            newbox.setParent(newcblock);
                            newcblock.addSubBox(newbox);
                            ((BlockBox) newbox).absReference = inflowReference; //set the reference box for computing the static position
                        }
                    }
                }
            }
            
            createAnonymousBlocks(root);
            createAnonymousBoxes(root, decoder,
                                 ElementBox.DISPLAY_TABLE_CELL,
                                 ElementBox.DISPLAY_TABLE_ROW, ElementBox.DISPLAY_ANY, ElementBox.DISPLAY_ANY, 
                                 "tr", "table-row");
            createAnonymousBoxes(root, decoder,
                                 ElementBox.DISPLAY_TABLE_ROW,
                                 ElementBox.DISPLAY_TABLE_ROW_GROUP, ElementBox.DISPLAY_TABLE_HEADER_GROUP, ElementBox.DISPLAY_TABLE_FOOTER_GROUP, 
                                 "tbody", "table-row-group");
            createAnonymousBoxes(root, decoder,
                                 ElementBox.DISPLAY_TABLE_ROW_GROUP,
                                 ElementBox.DISPLAY_TABLE, ElementBox.DISPLAY_ANY, ElementBox.DISPLAY_ANY,
                                 "table", "table");
            
            return root;
        }
    }

    /**
     * Creates anonymous block boxes as the a block box contains both the inline
     * and the block child boxes. The child boxes of the specified root
     * are processed and the inline boxes are grouped in a newly created
     * anonymous <code>div</code> boxes.
     * @param root the root box
     */
    private static void createAnonymousBlocks(ElementBox root)
    {
        if (root.isBlock() && ((BlockBox) root).containsBlocks())
        {
            Vector<Box> nest = new Vector<Box>();
            BlockBox adiv = null;
            for (int i = 0; i < root.getSubBoxNumber(); i++)
            {
                Box sub = root.getSubBox(i);
                if (sub.isblock)
                {
                    adiv = null;
                    nest.add(sub);
                }
                else if (!sub.isWhitespace()) //omit whitespace boxes
                {
                    if (adiv == null)
                    {
                        Element anelem = createAnonymousBox(root.getElement().getOwnerDocument(), "div", "block");
                        adiv = new BlockBox(anelem, root.getGraphics(), root.getVisualContext());
                        adiv.setStyle(createAnonymousBoxStyle("block"));
                        computeInheritedStyle(adiv, root);
                        adiv.isblock = true;
                        adiv.contblock = false;
                        adiv.isempty = true;
                        adiv.setViewport(root.getViewport());
                        adiv.setParent(root);
                        adiv.setContainingBlock(sub.getContainingBlock());
                        adiv.setClipBlock(sub.getClipBlock());
                        nest.add(adiv);
                    }
                    if (sub.isDisplayed() && !sub.isEmpty()) 
                    { 
                        adiv.isempty = false;
                        adiv.displayed = true;
                    }
                    sub.setParent(adiv);
                    adiv.addSubBox(sub);
                }
            }
            root.nested = nest;
            root.endChild = nest.size();
        }
    }
    
    /**
     * Checks the child boxes of the specified root box wheter they require creating an anonymous
     * parent box.
     * @param root the box whose child boxes are checked
     * @param decoder DOM style decoder used for obtaining the efficient node styles
     * @param type the required display type of the child boxes. The remaining child boxes are skipped.
     * @param reqtype1 the first required display type of the root. If the root type doesn't correspond
     * 		to any of the required types, an anonymous parent is created for the selected children.
     * @param reqtype3 the second required display type of the root.
     * @param reqtype4 the third required display type of the root.
     * @param the element name of the created anonymous box
     * @param the display type of the created anonymous box
     */
    private static void createAnonymousBoxes(ElementBox root, DOMAnalyzer decoder, 
                                             CSSProperty.Display type,
                                             CSSProperty.Display reqtype1, 
                                             CSSProperty.Display reqtype2, 
                                             CSSProperty.Display reqtype3, 
    		                                 String name, String display)
    {
    	if (root.getDisplay() != reqtype1 && root.getDisplay() != reqtype2 && root.getDisplay() != reqtype3)
    	{
	        Vector<Box> nest = new Vector<Box>();
	        ElementBox adiv = null;
	        for (int i = 0; i < root.getSubBoxNumber(); i++)
	        {
	            Box sub = root.getSubBox(i);
	            if (sub instanceof ElementBox)
	            {
	            	ElementBox subel = (ElementBox) sub;
		            if (subel.getDisplay() != type)
		            {
		                adiv = null;
		                nest.add(sub);
		            }
		            else if (!sub.isWhitespace()) //omit whitespace boxes
		            {
		                if (adiv == null)
		                {
		                	Element elem = createAnonymousBox(root.getElement().getOwnerDocument(), name, display);
		                	adiv = createBox(elem, root.getGraphics(), root.getVisualContext(), decoder, root.getBase(), root.getViewport(), root, display);
		                    adiv.isblock = true;
		                    adiv.isempty = true;
		                    adiv.setViewport(root.getViewport());
		                    adiv.setParent(root);
		                    adiv.setContainingBlock(sub.getContainingBlock());
                            adiv.setClipBlock(sub.getClipBlock());
		                    nest.add(adiv);
		                }
		                if (sub.isDisplayed() && !sub.isEmpty()) 
		                { 
		                    adiv.isempty = false;
		                    adiv.displayed = true;
		                }
		                sub.setParent(adiv);
		                sub.setContainingBlock((BlockBox) adiv);
		                adiv.addSubBox(sub);
		            }
	            }
	            else
	            	return; //first box is TextBox => all the boxes are TextBox, nothing to do. 
	        }
	        root.nested = nest;
	        root.endChild = nest.size();
    	}
    }
    
    /**
     * Creates a new box from an element.
     * @param n The source DOM element
     * @param g Graphics context
     * @param ctx Visual context
     * @param decoder The CSS style analyzer
     * @param baseurl The base URL of the document
     * @param viewport The used viewport
     * @param parent the root element from which the style will be inherited
     * @param display the display: property value that is used when the box style is not known (e.g. anonymous boxes)
     * @return A new box of a subclass of ElementBox based on the value of the 'display' CSS property
     */
    public static ElementBox createBox(Element n, Graphics2D g, VisualContext ctx, 
                                       DOMAnalyzer decoder, URL baseurl, Viewport viewport,
                                       ElementBox parent, String display)
    {
	    ElementBox root;
        
        //New box style
        NodeData style = decoder.getElementStyleInherited(n);
        if (style == null)
        		style = createAnonymousBoxStyle(display);
        
        //Special tag names
        if (n.getNodeName().equals("img"))
        {
        	InlineReplacedBox rbox = new InlineReplacedBox((Element) n, g, ctx);
            rbox.setStyle(style);
        	rbox.setContentObj(new ReplacedImage(rbox, ctx, baseurl));
        	root = rbox;
        	if (root.isBlock())
        		root = new BlockReplacedBox(rbox);
        }
        //Convert the box type according to the <code>display</code> value
        else
        {
    	    root = new InlineBox((Element) n, g, ctx);
            root.setStyle(style);
    	    if (root.getDisplay() == ElementBox.DISPLAY_LIST_ITEM)
    	    	root = new ListItemBox((InlineBox) root);
            else if (root.getDisplay() == ElementBox.DISPLAY_TABLE)
                root = new TableBox((InlineBox) root);
            else if (root.getDisplay() == ElementBox.DISPLAY_TABLE_CAPTION)
                root = new TableCaptionBox((InlineBox) root);
            else if (root.getDisplay() == ElementBox.DISPLAY_TABLE_ROW_GROUP
                     || root.getDisplay() == ElementBox.DISPLAY_TABLE_HEADER_GROUP
                     || root.getDisplay() == ElementBox.DISPLAY_TABLE_FOOTER_GROUP)
                root = new TableBodyBox((InlineBox) root);
            else if (root.getDisplay() == ElementBox.DISPLAY_TABLE_ROW)
                root = new TableRowBox((InlineBox) root);
            else if (root.getDisplay() == ElementBox.DISPLAY_TABLE_CELL)
                root = new TableCellBox((InlineBox) root);
            else if (root.getDisplay() == ElementBox.DISPLAY_TABLE_COLUMN)
                root = new TableColumn((InlineBox) root);
            else if (root.getDisplay() == ElementBox.DISPLAY_TABLE_COLUMN_GROUP)
                root = new TableColumnGroup((InlineBox) root);
    	    else if (root.isBlock())
    	        root = new BlockBox((InlineBox) root);
        }
        root.setBase(baseurl);
        root.setViewport(viewport);
        root.setOrder(next_order++);
    	return root;
    }
    
    /**
     * Creates a new <div> element that represents an anonymous box in a document.
     * @param doc the document
     * @param name the anonymous element name (generally arbitrary)
     * @param display the display style value for the block
     * @return the new element
     */
    private static Element createAnonymousBox(Document doc, String name, String display)
    {
        Element div = doc.createElement(name);
        div.setAttribute("class", "Xanonymous");
        div.setAttribute("style", "display:" + display);
        return div;
    }
    
    private static NodeData createAnonymousBoxStyle(String display)
    {
        NodeData ret = CSSFactory.createNodeData();
        
        Declaration cls = CSSFactory.getRuleFactory().createDeclaration();
        cls.unlock();
        cls.setProperty("class");
        cls.add(CSSFactory.getTermFactory().createString("Xanonymous"));
        ret.push(cls);
        
        Declaration disp = CSSFactory.getRuleFactory().createDeclaration();
        disp.unlock();
        disp.setProperty("display");
        disp.add(CSSFactory.getTermFactory().createIdent(display));
        ret.push(disp);
        
        return ret;
    }
    
    /**
     * Computes the style of a node based on its parent using the CSS inheritance.
     * @param dest the box whose style should be computed
     * @param parent the parent box
     */ 
    private static void computeInheritedStyle(ElementBox dest, ElementBox parent)
    {
    	NodeData newstyle = dest.getStyle().inheritFrom(parent.getStyle()); 
    	dest.setStyle(newstyle);
    }
   
    /**
     * Recursively determines the containing boxes for all boxes in a (sub)tree
     * @param root the (sub)tree root
     * @param contbox the containing box of the root box when not absolutly positioned
     * @param absbox the containing box of the root box when absolutly positioned
     */
    private static void updateContainingBoxes(ElementBox root, BlockBox contbox, BlockBox absbox)
    {
        BlockBox newcont = contbox;
        BlockBox newabs = absbox;
        
        if (root.isBlock())
        {
            BlockBox block = (BlockBox) root;
            //My containing box
            if (block.position == BlockBox.POS_ABSOLUTE ||
                block.position == BlockBox.POS_FIXED)    
                root.setContainingBlock(absbox);
            else    
                root.setContainingBlock(contbox);
            //A positioned box forms a content box for following absolutely
            //positioned boxes
            if (block.position == BlockBox.POS_ABSOLUTE ||
                block.position == BlockBox.POS_RELATIVE ||
                block.position == BlockBox.POS_FIXED)
                newabs = block;
            //Any block box forms a containing box for not positioned elements
            newcont = block;
        }
        else
            root.setContainingBlock(contbox);

        for (int i = 0; i < root.getSubBoxNumber(); i++)
        {
                Box sub = root.getSubBox(i);
                if (sub instanceof ElementBox)
                    updateContainingBoxes((ElementBox) sub, newcont, newabs);
                else
                    sub.setContainingBlock(contbox);
        }        
    }
    
    //========================================================================
        
    /**
     * Returns the DOM node that forms this box.
     * @return the DOM node
     */
    public Node getNode()
    {
        return node;
    }

    /**
     * Gets the order of the node in the document.
	 * @return the order
	 */
	public int getOrder()
	{
		return order;
	}

	/**
     * Sets the order of the node in the document.
	 * @param order the order to set
	 */
	public void setOrder(int order)
	{
		this.order = order;
	}

	/**
     * Returns the style of the DOM node that forms this box.
     * @return the style declaration
     */
    public NodeData getStyle()
    {
    	return style;
    }
    
    public String getStyleString()
    {
        if (style != null)
            return style.toString();
        else
            return "";
    }
    
    /**
     * Assign a new style to this box
     * @param s the new style declaration
     */
    public void setStyle(NodeData s)
    {
    	style = s;
    }
    
    /**
     *
     */
    public String getStylePropertyValue(String property)
    {
        Object t = style.getValue(Term.class, property);
        if (t == null)
            return "";
        else
            return t.toString();
    }
    
    
    /**
     * Returns the graphics context that is used for rendering.
     * @return the graphics context
     */
    public Graphics2D getGraphics()
    {
        return g;
    }

    /**
     * @return the visual context of this box
     */
    public VisualContext getVisualContext()
    {
        return ctx;
    }
    
    /** 
     * Checks if this is a block box.
     * @return false if this is an inline box and it contains inline
     * boxes only, true otherwise.
     */
    public boolean isBlock()
    {
        return isblock;
    }
    
    /** 
     * @return <code>true</code>, if this element contains no visible non-empty elements
     */
    public boolean isEmpty()
    {
        return isempty;
    }
    
    /**
     * @return <code>true</code>, if this element has the 'display' property different from 'none'
     */
    public boolean isDisplayed()
    {
        return displayed;
    }

    /**
     * Checks if this box is visible, i.e. it has not visibility:hidden and it is at least partially
     * contained in the clipping region.
     * @return <code>true</code> if the element is visible
     */
    public boolean isVisible()
    {
        return visible && clipblock.absbounds.intersects(absbounds);
    }
    
    /**
     * @return <code> true if this is a replaced box
     */
    public boolean isReplaced()
    {
    	return false;
    }
    
    /**
     * @return all the text contained in this box and its subboxes
     */
    abstract public String getText();
    
    /**
     * @return <code>true</code> if the box only contains whitespaces
     */
    abstract public boolean isWhitespace();
    
    /**
     * @return <code>true</code> if the box can be split in two or more boxes
     * on different lines
     */
    abstract public boolean canSplitInside();
    
    /**
     * @return <code>true</code> if there can be a linebreak before this element
     * on different lines
     */
    abstract public boolean canSplitBefore();
    
    /**
     * @return <code>true</code> if there can be a linebreak after this element
     * on different lines
     */
    abstract public boolean canSplitAfter();
    
    /**
     * Set the box position.
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public void setPosition(int x, int y)
    {
        bounds.setLocation(x, y);
    }

    /**
     * Move the box to the right.
     * @param ofs distance in pixels - positive numbers move to the right, negative to the left
     */
    public void moveRight(int ofs)
    {
        bounds.x += ofs;
    }
    
    /**
     * Move the box down.
     * @param ofs distance in pixels - positive numbers move down, negative up
     */
    public void moveDown(int ofs)
    {
        bounds.y += ofs;
    }
    
    /**
     * Set the box total width and height.
     * @param w total box width including margins and borders
     * @param h total height
     */
    public void setSize(int w, int h)
    {
        bounds.setSize(w, h);
    }
    
    /**
     * Returns the real width of the box computed during the layout.
     * @return total width including margins and borders.
     */ 
    public int getWidth()
    {
        return bounds.width;
    }
    
    /**
     * Returns the real height of the box computed during the layout.
     * @return total height including margins and borders
     */ 
    public int getHeight()
    {
        return bounds.height;
    }
    
    /**
     * Returns maximal box bounds including all borders and margins.
     * @return Box bounds
     */
    public Rectangle getBounds()
    {
        return bounds;
    }
    
    /**
     * Returns the absolute box position on the page
     * @return Absolute box bounds
     */
    public Rectangle getAbsoluteBounds()
    {
    	return absbounds;
    }
    

    /**
     * Adjusts the absolute bounds width and height in order to fit into the clip.
     * If the box doesn't fit at all, it is marked as invisible.
     */
    public void clipAbsoluteBounds(Rectangle clip)
    {
        Rectangle inter = absbounds.intersection(clip);
        if (inter.width == 0 && inter.height == 0)
            displayed = false;
        else
            absbounds = inter;
    }
    
    
    /**
     * @return maximal width that was available for the box placement during the layout processing
     */
    public int getAvailableWidth()
    {
        return availwidth;
    }
    
    /**
     * Set the maximal width available to the box
     * @param availw the maximal available width
     */
    public void setAvailableWidth(int availw)
    {
        availwidth = availw;
    }
    
    /**
     * @return the containing block of this box according to the 
     * <a href="http://www.w3.org/TR/CSS21/visudet.html#containing-block-details">CSS specification</a>
     */
    public BlockBox getContainingBlock()
    {
        return cblock;
    }
    
    /**
     * Set the containing block. During the layout, the box position will be
     * computed inside of the containing block.
     * @param box the containing box
     */
    public void setContainingBlock(BlockBox box)
    {
        cblock = box;
    }
    
    /**
     * Determines the clipping box. The box is not visible if it is not inside of the clipping box. 
     * Normally, the clipping box is the viewport or the nearest parent with overflow set to hidden. 
     * @return the clipping block
     */
    public BlockBox getClipBlock()
    {
        return clipblock;
    }

    /**
     * Sets the clipping block.
     * @param clipblock the clipblock to set
     */
    public void setClipBlock(BlockBox clipblock)
    {
        this.clipblock = clipblock;
    }
    
    /**
     * @return the expected width of the box according to the CSS property values
     */ 
    abstract public int totalWidth();
    
    /**
     * @return the expected height of the box according to the CSS property values
     */ 
    abstract public int totalHeight();
    
    /**
     * @return the computed line height for the box
     */
    abstract public int getLineHeight();
    
    /**
     * @return maximal available width of the content during the layout
     */
    abstract public int getAvailableContentWidth();
    
    /**
     * @return the X coordinate of the content box top left corner
     */
    abstract public int getContentX();
    
    /**
     * @return the absolute X coordinate of the content box top left corner
     */
    abstract public int getAbsoluteContentX();
    
    /**
     * @return the Y coordinate of the content box top left corner
     */
    abstract public int getContentY();

    /**
     * @return the Y coordinate of the content box top left corner
     */
    abstract public int getAbsoluteContentY();
    
    /**
     * @return the width of the content without any margins and borders
     */
    abstract public int getContentWidth();
    
    /**
     * @return the height of the content without any margins and borders
     */
    abstract public int getContentHeight();
    
    /**
     * @return the bounds of the content box
     */
    public Rectangle getContentBounds()
    {
        return new Rectangle(getContentX(), getContentY(), getContentWidth(), getContentHeight());
    }
    
    /**
     * @return the absolute bounds of the content box
     */
    public Rectangle getAbsoluteContentBounds()
    {
        return new Rectangle(getAbsoluteContentX(), getAbsoluteContentY(), getContentWidth(), getContentHeight());
    }
    
    /**
     * Determines the minimal width in which the element can fit.
     * @return the minimal width
     */
    abstract public int getMinimalWidth();
    
    /**
     * 
     * Determines the maximal width of the element according to its contents.
     * @return the maximal width
     */
    abstract public int getMaximalWidth();
    
    /**
     * Determines the minimal bounds of the really displayed content.
     * @return the minimal bounds
     */
    abstract public Rectangle getMinimalAbsoluteBounds();
    
    /**
     * @return true, if the box is in-flow
     */
    abstract public boolean isInFlow();

    /**
     * @return <code>true</code> if the width of the box is either explicitely
     * set or it can be computed from the parent box
     */
    abstract public boolean hasFixedWidth();
    
    /**
     * @return <code>true</code> if the height of the box is either explicitely
     * set or it can be computed from the parent box
     */
    abstract public boolean hasFixedHeight();
    
    /**
     * @return true, if the box contains any in-flow boxes
     */
    abstract public boolean containsFlow();
    
    /**
     * @return true, if the element displays at least something (some content,
     * or borders) 
     */
    abstract public boolean affectsDisplay();

    /**
	 * @return the viewport
	 */
	public Viewport getViewport()
	{
		return viewport;
	}

	/**
	 * @param viewport the viewport to set
	 */
	public void setViewport(Viewport viewport)
	{
		this.viewport = viewport;
	}

	/**
	 * @return Returns the parent.
	 */
	public ElementBox getParent()
	{
		return parent;
	}

	/**
	 * @param parent The parent to set.
	 */
	public void setParent(ElementBox parent)
	{
		this.parent = parent;
	}
	
	/**
     * @return the base URL
     */
    public URL getBase()
    {
        return base;
    }

    /**
     * @param base the base URL to set
     */
    public void setBase(URL base)
    {
        this.base = base;
    }

    /**
	 * When the box doesn't fit to the line, it can be split in two boxes.
	 * The first one remains at its place and the rest must be placed elsewhere.
	 * The splitting algorithm depends on the box type.
	 * @return the rest of the box
	 */
    protected Box getRest()
    {
        return rest;
    }
    
    /**
     * Reads a CSS length value of a style property of the box.
     * @param name property name
     * @return the length value
     */
    public TermLengthOrPercent getLengthValue(String name)
    {
        if (style != null)
            return style.getValue(TermLengthOrPercent.class, name);
        else
            return null;
    }
    
    /**
     * Reads the value of a border width specified by a CSS property.
     * @param dec a CSS decoder used for converting the values
     * @param property, the property name, e.g. "border-top-width"
     * @return the border width in pixels
     */
    public int getBorderWidth(CSSDecoder dec, String property)
    {
    	if (style != null)
    	{
    		CSSProperty.BorderWidth prop = style.getProperty(property);
    		if (prop == CSSProperty.BorderWidth.length)
    			return dec.getLength(style.getValue(TermLengthOrPercent.class, property), false, CSSUnits.MEDIUM_BORDER, 0, 0);
    		else
    			return CSSUnits.convertBorderWidth(prop);
    	}
    	else
    		return 0;
    }
    
    //========================================================================
    
    /** 
     * Compute the width and height of this element. Layout the sub-elements.
     * @param availw Maximal width available to the child elements
     * @param force Use the area even if the used width is greater than maxwidth
     * @param linestart Indicates whether the element is placed at the line start
     * @return True if the box has been succesfully placed
     */
    abstract public boolean doLayout(int availw, boolean force, boolean linestart);
    

    /** 
     * Calculate absolute positions of all the subboxes.
     */
    abstract public void absolutePositions();
    
    
    //=======================================================================
    
    /** 
     * Draw the box and all the subboxes on the default
     * graphics context passed to the box constructor. 
     */
    public void draw()
    {
        draw(g);
    }
    
    /**
     * Draw the box and all the subboxes.
     * @param g graphics context to draw on
     */
    public void draw(Graphics2D g)
    {
        if (isVisible())
        {
            draw(g, DRAW_NONFLOAT, DRAW_BOTH);
            draw(g, DRAW_FLOAT, DRAW_BOTH);
            draw(g, DRAW_NONFLOAT, DRAW_FG);
        }
    }
    
    /**
     * Draw the specified stage (DRAW_*)
     * @param g graphics context to draw on
     * @param turn drawing stage - DRAW_ALL, DRAW_FLOAT or DRAW_NONFLOAT
     * @param mode what to draw - DRAW_FG, DRAW_BG or DRAW_BOTH 
     */
    abstract public void draw(Graphics2D g, int turn, int mode);

    /**
     * Draw the bounds of the box (for visualisation).
     */
    abstract public void drawExtent(Graphics2D g);

}
