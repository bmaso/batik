/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.refimpl.gvt.filter;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderContext;

import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.filter.CachableRed;
import org.apache.batik.gvt.filter.Filter;
import org.apache.batik.gvt.filter.TileRable;

public class ConcreteTileRable extends AbstractRable implements TileRable{
    /**
     * Tile region
     */
    private Rectangle2D tileRegion;

    /**
     * Tiled region
     */
    private Rectangle2D tiledRegion;

    /**
     * Controls whether the tileRegion clips the source
     * or not
     */
    private boolean overflow;

    /**
     * Returns the tile region
     */
    public Rectangle2D getTileRegion(){
        return tileRegion;
    }

    /**
     * Sets the tile region
     */
    public void setTileRegion(Rectangle2D tileRegion){
        if(tileRegion == null){
            throw new IllegalArgumentException();
        }
        this.tileRegion = tileRegion;
    }

    /**
     * Returns the tiled region
     */
    public Rectangle2D getTiledRegion(){
        return tiledRegion;
    }

    /**
     * Sets the tiled region
     */
    public void setTiledRegion(Rectangle2D tiledRegion){
        if(tiledRegion == null){
            throw new IllegalArgumentException();
        }
        this.tiledRegion = tiledRegion;
    }

    /**
     * Returns the overflow strategy
     */
    public boolean isOverflow(){
        return overflow;
    }

    /**
     * Sets the overflow strategy
     */
    public void setOverflow(boolean overflow){
        this.overflow = overflow;
    }

    /**
     * Default constructor
     */
    public ConcreteTileRable(Filter source, 
                             Rectangle2D tiledRegion,
                             Rectangle2D tileRegion,
                             boolean overflow){
        super(source);

        setTileRegion(tileRegion);
        setTiledRegion(tiledRegion);
        setOverflow(overflow);
    }

    /**
     * Sets the filter source
     */
    public void setSource(Filter src){
        init(src);
    }

    /**
     * Return's the tile source
     */
    public Filter getSource(){
        return (Filter)srcs.get(0);
    }

    /**
     * Returns this filter's bounds
     */
    public Rectangle2D getBounds2D(){
        return (Rectangle2D)tiledRegion.clone();
    }

    public RenderedImage createRendering(RenderContext rc){
        // Just copy over the rendering hints.
        RenderingHints rh = rc.getRenderingHints();
        if (rh == null) rh = new RenderingHints(null);

        // update the current affine transform
        AffineTransform at = rc.getTransform();

        double sx = at.getScaleX();
        double sy = at.getScaleY();

        double shx = at.getShearX();
        double shy = at.getShearY();

        double tx = at.getTranslateX();
        double ty = at.getTranslateY();

        // The Scale is the "hypotonose" of the matrix vectors.
        double scaleX = Math.sqrt(sx*sx + shy*shy);
        double scaleY = Math.sqrt(sy*sy + shx*shx);

        //
        // Compute the actual tiled area (intersection of AOI
        // and bounds) and the actual tile (anchored in the
        // upper left corner of the tiled area
        //

        // tiledRect
        Rectangle2D tiledRect = getBounds2D();
        Shape       aoiShape  = rc.getAreaOfInterest();
        Rectangle2D aoiRect   = aoiShape.getBounds2D();

        if (tiledRect.intersects(aoiRect) == false) 
            return null;
        Rectangle2D.intersect(tiledRect, aoiRect, tiledRect);

        // tileRect
        Rectangle2D tileRect = getActualTileBounds(tiledRect);
        
        // Adjust the scale so that the tiling happens on pixel
        // boundaries on both axis.
        // Desired pixel rect width
        int dw = (int)(Math.ceil(tileRect.getWidth()*scaleX));
        int dh = (int)(Math.ceil(tileRect.getHeight()*scaleY));

        double tileScaleX = dw/tileRect.getWidth();
        double tileScaleY = dh/tileRect.getHeight();

        // System.out.println("scaleX/scaleY : " + scaleX + " / " + scaleY);
        // System.out.println("tileScaleX/tileScaleY : " + tileScaleX + " / " + tileScaleY);

        // Adjust the translation so that the tile's origin falls on
        // pixel boundary
        int dx = (int)(Math.floor(tileRect.getX()*tileScaleX));
        int dy = (int)(Math.floor(tileRect.getY()*tileScaleY));

        double ttx = dx - tileRect.getX()*tileScaleX;
        double tty = dy - tileRect.getY()*tileScaleY;

        // System.out.println("ttx/tty : " + ttx + " / " + tty);

        // Get result unsheared or rotated
        AffineTransform tileAt = 
            AffineTransform.getTranslateInstance(ttx, tty);
        tileAt.scale(tileScaleX, tileScaleY);

        // System.out.println("tileRect in userSpace   : " + tileRect);
        // System.out.println("tileRect in deviceSpace : " + tileAt.createTransformedShape(tileRect).getBounds2D());

        RenderContext tileRc 
            = new RenderContext(tileAt,
                                rc.getAreaOfInterest(),
                                rh);

        RenderedImage tileRed = createTile(tileRc);
        // System.out.println("tileRed : " + tileRed.getMinX() + "/" + tileRed.getMinY() + "/" 
        // + tileRed.getWidth() + "/" + tileRed.getHeight());
        if(tileRed == null){
            return null;
        }

        Rectangle tiledArea = tileAt.createTransformedShape(rc.getAreaOfInterest()).getBounds();
        if(tiledArea.width <= 0 || tiledArea.height <= 0){
            return null;
        }

        RenderedImage tiledRed 
            = new TileRed(tiledArea, tileRed);

        /*org.apache.batik.test.gvt.ImageDisplay.showImage("Tile",
          tileRed);*/

        // Return sheared/rotated tiled image
        AffineTransform shearAt =
            new AffineTransform(sx/scaleX, shy/scaleX,
                                shx/scaleY, sy/scaleY,
                                tx, ty);
        
        shearAt.scale(scaleX/tileScaleX, scaleY/tileScaleY);
        shearAt.translate(-ttx, -tty);
        
        if(shearAt.isIdentity()){
            return tiledRed;
        }
       
        CachableRed cr 
            = new ConcreteRenderedImageCachableRed(tiledRed);

        cr = new AffineRed(cr, shearAt, rh);

        return cr;
    }

    public Rectangle2D getActualTileBounds(Rectangle2D tiledRect){
        // Get the tile rectangle in user space
        Rectangle2D tileRect = (Rectangle2D)tileRegion.clone();

        // System.out.println("tileRect : " + tileRect);
        // System.out.println("tiledRect: " + tiledRect);

        if ((tileRect.getWidth()   <= 0)
            || (tileRect.getHeight()  <= 0)
            || (tiledRect.getWidth()  <= 0)
            || (tiledRect.getHeight() <= 0))
            return null;


        double tileWidth = tileRect.getWidth();
        double tileHeight = tileRect.getHeight();
        
        double tiledWidth = tiledRect.getWidth();
        double tiledHeight = tiledRect.getHeight();

        double w = Math.min(tileWidth, tiledWidth);
        double h = Math.min(tileHeight, tiledHeight);

        Rectangle2D realTileRect 
            = new Rectangle2D.Double(tiledRect.getX(),
                                     tiledRect.getY(),
                                     w, h);

        return realTileRect;
    }

    /**
     * Computes the tile to use for the tiling operation.
     * 
     * The tile has its origin in the upper left
     * corner of the tiled region. That tile is separated 
     * into 4 areas: top-left, top-right, bottom-left and
     * bottom-right. Each of these areas is mapped to 
     * some input area from the source.
     * If the source is smaller than the tiled area, then
     * a single rendering is requested from the source.
     * If the source's width or height is bigger than that
     * of the tiled area, then separate renderings are 
     * requested from the source.
     * 
     */
    public RenderedImage createTile(RenderContext rc){
        // Rendered result
        RenderedImage result = null;

        AffineTransform usr2dev = rc.getTransform();

        // Hints
        RenderingHints hints = rc.getRenderingHints();
        if(hints == null){
            hints = new RenderingHints(null);
        }

        hints = new RenderingHints(hints);

        // The region actually tiles is the intersection
        // of the tiledRegion and the area of interest
        Rectangle2D tiledRect = getBounds2D();
        Shape       aoiShape  = rc.getAreaOfInterest();
        Rectangle2D aoiRect   = aoiShape.getBounds2D();
        if (tiledRect.intersects(aoiRect) == false) 
            return null;
        Rectangle2D.intersect(tiledRect, aoiRect, tiledRect);

        // Get the tile rectangle in user space
        Rectangle2D tileRect = (Rectangle2D)tileRegion.clone();

        // System.out.println("tileRect : " + tileRect);
        // System.out.println("tiledRect: " + tiledRect);

        if ((tileRect.getWidth()   <= 0)
            || (tileRect.getHeight()  <= 0)
            || (tiledRect.getWidth()  <= 0)
            || (tiledRect.getHeight() <= 0))
            return null;

        //
        // (tiledX, tiledY)
        //                    <------- min(tileWidth, tiledWidth) ----------->
        //                    ^ +------+-------------------------------------+
        //                    | +  A'  +                   B'                +
        //                    | +------+-------------------------------------+
        // min(tileHeight,    | +      +                                     +
        //     tiledHeight)   | +      +                                     +
        //                    | +  C'  +                   D'                +
        //                    | +      +                                     +
        //                    ^ +------+-------------------------------------+
        //
        // Maps to, in the tile:
        //
        // (tileX, tileY)
        // 
        //                    <-----------      tileWidth     --------------->
        //                    ^ +-----------------------------+------+-------+
        //                    | +                             +      +       |
        //     tiledHeight    | +                             +      +       |
        //                    | +               D             +      +   C   |
        //                    | +                             +      +       |
        //                    | +-----------------------------+------+-------|
        //                    | +                             |      |       |
        //                    | +                             |      |       |
        //                    | +-----------------------------+------+-------+
        //                    | |               B             +      +   A   |
        //                    ^ +-----------------------------+------+-------+
            
        // w  = min(tileWidth, tiledWidth)
        // h  = min(tileHeight, tiledHeight)
        // dx = tileWidth  - (tiledX - tileX)%tileWidth;
        // dy = tileHeight - (tiledY - tileY)%tileHeight;
        //
        // A = (tileX + tileWidth - dx, tileY + tileHeight - dy, dx, dy)
        // B = (tileX, tileY + tileHeight - dy, w - dx, dy)
        // C = (tileX + tileWidth - dx, tileY, dx, h - dy)
        // D = (tileX, tileY, w - dx, h - dy)

        double tileX = tileRect.getX();
        double tileY = tileRect.getY();
        double tileWidth = tileRect.getWidth();
        double tileHeight = tileRect.getHeight();
        
        double tiledX = tiledRect.getX();
        double tiledY = tiledRect.getY();
        double tiledWidth = tiledRect.getWidth();
        double tiledHeight = tiledRect.getHeight();

        double w = Math.min(tileWidth, tiledWidth);
        double h = Math.min(tileHeight, tiledHeight);
        double dx = (tiledX - tileX)%tileWidth;
        double dy = (tiledY - tileY)%tileHeight;

        if(dx > 0){
            dx = tileWidth - dx;
        }
        else{
            dx *= -1;
        }

        if(dy > 0){
            dy = tileHeight - dy;
        }
        else{
            dy *= -1;
        }

        //
        // Adjust dx and dy so that they fall on a pixel boundary
        //
        double scaleX = usr2dev.getScaleX();
        double scaleY = usr2dev.getScaleY();
        double tdx = Math.floor(scaleX*dx);
        double tdy = Math.floor(scaleY*dy);

        dx = tdx/scaleX;
        dy = tdy/scaleY;

        // System.out.println("dx / dy / w / h : " + dx + " / " + dy + " / " + w + " / " + h);

        Rectangle2D.Double A = new Rectangle2D.Double
            (tileX + tileWidth - dx, tileY + tileHeight - dy, dx, dy);
        Rectangle2D.Double B = new Rectangle2D.Double
            (tileX, tileY + tileHeight - dy, w - dx, dy);
        Rectangle2D.Double C = new Rectangle2D.Double
            (tileX + tileWidth - dx, tileY, dx, h - dy);
        Rectangle2D.Double D = new Rectangle2D.Double
            (tileX, tileY, w - dx, h - dy);

        Rectangle2D realTileRect 
            = new Rectangle2D.Double(tiledRect.getX(),
                                     tiledRect.getY(),
                                     w, h);

        // System.out.println("A rect    : " + A);
        // System.out.println("B rect    : " + B);
        // System.out.println("C rect    : " + C);
        // System.out.println("D rect    : " + D);
        // System.out.println("realTileR : " + realTileRect);
        
        // A, B, C and D are the four user space are that make the
        // tile that will be used. We create a rendering for each of
        // these areas that i s not empty (i.e., with either width or
        // height equal to zero)
        RenderedImage ARed = null, BRed = null, CRed = null, DRed = null;
        Filter source = getSource();
        
        if (A.getWidth() > 0 && A.getHeight() > 0){
            // System.out.println("Rendering A");
            Rectangle devA = usr2dev.createTransformedShape(A).getBounds();
            if(devA.width > 0 && devA.height > 0){
                AffineTransform ATxf = new AffineTransform(usr2dev);
                ATxf.translate(-A.x + tiledX,
                               -A.y + tiledY);

                Shape aoi = A;
                if(overflow){
                    aoi = new Rectangle2D.Double(A.x, 
                                                 A.y,
                                                 tiledWidth,
                                                 tiledHeight);
                }

                hints.put(GraphicsNode.KEY_AREA_OF_INTEREST,
                          aoi);

                RenderContext arc 
                    = new RenderContext(ATxf, aoi, hints);

                ARed = source.createRendering(arc);
                
                //System.out.println("ARed : " + ARed.getMinX() + " / " + 
                //                   ARed.getMinY() + " / " + 
                //                   ARed.getWidth() + " / " + 
                //                   ARed.getHeight());
            }
        }

        if(B.getWidth() > 0 && B.getHeight() > 0){
            // System.out.println("Rendering B");
            Rectangle devB = usr2dev.createTransformedShape(B).getBounds();
            if(devB.width > 0 && devB.height > 0){
                AffineTransform BTxf = new AffineTransform(usr2dev);
                BTxf.translate(-B.x + (tiledX + dx),
                               -B.y + tiledY);

                Shape aoi = B;
                if(overflow){
                    aoi = new Rectangle2D.Double(B.x - tiledWidth + w - dx,
                                                 B.y,
                                                 tiledWidth,
                                                 tiledHeight);
                }

                hints.put(GraphicsNode.KEY_AREA_OF_INTEREST,
                          aoi);

                RenderContext brc 
                    = new RenderContext(BTxf, aoi, hints);

                BRed = source.createRendering(brc);
                // System.out.println("BRed : " + BRed.getMinX() + " / " + BRed.getMinY() + " / " + BRed.getWidth() + " / " + BRed.getHeight());
            }
        }

        if(C.getWidth() > 0 && C.getHeight() > 0){
            // System.out.println("Rendering C");
            Rectangle devC = usr2dev.createTransformedShape(C).getBounds();
            if(devC.width > 0 && devC.height > 0){
                AffineTransform CTxf = new AffineTransform(usr2dev);
                CTxf.translate(-C.x + tiledX,
                               -C.y + (tiledY + dy));

                Shape aoi = C;
                if(overflow){
                    aoi = new Rectangle2D.Double(C.x,
                                                 C.y - tileHeight + h - dy,
                                                 tiledWidth,
                                                 tiledHeight);
                }

                hints.put(GraphicsNode.KEY_AREA_OF_INTEREST,
                          aoi);

                RenderContext crc 
                    = new RenderContext(CTxf, aoi, hints);

                CRed = source.createRendering(crc);
                // System.out.println("CRed : " + CRed.getMinX() + " / " + CRed.getMinY() + " / " + CRed.getWidth() + " / " + CRed.getHeight());
            }
        }

        if(D.getWidth() > 0 && D.getHeight() > 0){
            // System.out.println("Rendering D");
            Rectangle devD = usr2dev.createTransformedShape(D).getBounds();
            if(devD.width > 0 && devD.height > 0){
                AffineTransform DTxf = new AffineTransform(usr2dev);
                DTxf.translate(-D.x + (tiledX + dx),
                               -D.y + (tiledY + dy));

                Shape aoi = D;
                if(overflow){
                    aoi = new Rectangle2D.Double(D.x - tileWidth + w - dx,
                                                 D.y - tileHeight + h - dy,
                                                 tiledWidth,
                                                 tiledHeight);
                }

                hints.put(GraphicsNode.KEY_AREA_OF_INTEREST,
                          aoi);

                RenderContext drc 
                    = new RenderContext(DTxf, aoi, hints);

                DRed = source.createRendering(drc);
                // System.out.println("DRed : " + DRed.getMinX() + " / " + DRed.getMinY() + " / " + DRed.getWidth() + " / " + DRed.getHeight());
            }
        }

        //
        // Now, combine ARed, BRed, CRed and DRed into a single
        // RenderedImage that will be tiled
        //
        final Rectangle realTileRectDev 
            = usr2dev.createTransformedShape(realTileRect).getBounds();
        
        if(realTileRectDev.width == 0 || realTileRectDev.height == 0){
            return null;
        }

        BufferedImage realTileBI
            = new BufferedImage(realTileRectDev.width,
                                realTileRectDev.height,
                                BufferedImage.TYPE_INT_ARGB);
        
        Graphics2D g = realTileBI.createGraphics();
        // g.setPaint(new java.awt.Color(0, 255, 0, 64));
        // g.fillRect(0, 0, realTileBI.getWidth(), realTileBI.getHeight());
        g.translate(-realTileRectDev.x,
                    -realTileRectDev.y);

        // System.out.println("realTileRectDev " + realTileRectDev);

        AffineTransform redTxf = new AffineTransform();
        Point2D.Double redVec = new Point2D.Double();
        RenderedImage refRed = null;
        if(ARed != null){
            // System.out.println("Drawing A");
            g.drawRenderedImage(ARed, redTxf);
            refRed = ARed;
        }
        if(BRed != null){
            // System.out.println("Drawing B");

            if(refRed == null){
                refRed = BRed;
            }

            // Adjust B's coordinates
            redVec.x = dx;
            redVec.y = 0;
            usr2dev.deltaTransform(redVec, redVec);
            redVec.x = Math.floor(redVec.x) - (BRed.getMinX() - refRed.getMinX());
            redVec.y = Math.floor(redVec.y) - (BRed.getMinY() - refRed.getMinY());

            // System.out.println("BRed adjust : " + redVec);

                // redTxf.setToTranslation(redVec.x, redVec.y);
            g.drawRenderedImage(BRed, redTxf);
        }
        if(CRed != null){
            // System.out.println("Drawing C");

            if(refRed == null){
                refRed = CRed;
            }

            // Adjust C's coordinates
            redVec.x = 0;
            redVec.y = dy;
            usr2dev.deltaTransform(redVec, redVec);
            redVec.x = Math.floor(redVec.x) - (CRed.getMinX() - refRed.getMinX());
            redVec.y = Math.floor(redVec.y) - (CRed.getMinY() - refRed.getMinY());

            // System.out.println("CRed adjust : " + redVec);

                // redTxf.setToTranslation(redVec.x, redVec.y);
            g.drawRenderedImage(CRed, redTxf);
        }
        if(DRed != null){
            // System.out.println("Drawing D");

            if(refRed == null){
                refRed = DRed;
            }

            // Adjust D's coordinates
            redVec.x = dx;
            redVec.y = dy;
            usr2dev.deltaTransform(redVec, redVec);
            redVec.x = Math.floor(redVec.x) - (DRed.getMinX() - refRed.getMinX());
            redVec.y = Math.floor(redVec.y) - (DRed.getMinY() - refRed.getMinY());

            // System.out.println("DRed adjust : " + redVec);

                // redTxf.setToTranslation(redVec.x, redVec.y);
            g.drawRenderedImage(DRed, redTxf);
        }

        CachableRed realTile;
        realTile = new ConcreteBufferedImageCachableRed(realTileBI,
                                                        realTileRectDev.x,
                                                        realTileRectDev.y);

        return realTile;
    }
}
