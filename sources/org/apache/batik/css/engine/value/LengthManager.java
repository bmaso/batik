/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.css.engine.value;

import org.apache.batik.css.engine.CSSContext;
import org.apache.batik.css.engine.CSSEngine;
import org.apache.batik.css.engine.CSSStylableElement;
import org.apache.batik.css.engine.StyleMap;

import org.w3c.css.sac.LexicalUnit;

import org.w3c.dom.DOMException;

import org.w3c.dom.css.CSSValue;
import org.w3c.dom.css.CSSPrimitiveValue;

/**
 * This class provides a manager for the property with support for
 * length values.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id$
 */
public abstract class LengthManager extends AbstractValueManager {
    
    /**
     * Implements {@link ValueManager#createValue(LexicalUnit,CSSEngine)}.
     */
    public Value createValue(LexicalUnit lu, CSSEngine engine)
        throws DOMException {
	switch (lu.getLexicalUnitType()) {
	case LexicalUnit.SAC_EM:
	    return new FloatValue(CSSPrimitiveValue.CSS_EMS,
                                  lu.getFloatValue());

	case LexicalUnit.SAC_EX:
	    return new FloatValue(CSSPrimitiveValue.CSS_EXS,
                                  lu.getFloatValue());

	case LexicalUnit.SAC_PIXEL:
	    return new FloatValue(CSSPrimitiveValue.CSS_PX,
                                  lu.getFloatValue());

	case LexicalUnit.SAC_CENTIMETER:
	    return new FloatValue(CSSPrimitiveValue.CSS_CM,
                                  lu.getFloatValue());

	case LexicalUnit.SAC_MILLIMETER:
	    return new FloatValue(CSSPrimitiveValue.CSS_MM,
                                  lu.getFloatValue());

	case LexicalUnit.SAC_INCH:
	    return new FloatValue(CSSPrimitiveValue.CSS_IN,
                                  lu.getFloatValue());

	case LexicalUnit.SAC_POINT:
	    return new FloatValue(CSSPrimitiveValue.CSS_PT,
                                  lu.getFloatValue());

	case LexicalUnit.SAC_PICA:
	    return new FloatValue(CSSPrimitiveValue.CSS_PC,
                                  lu.getFloatValue());

	case LexicalUnit.SAC_INTEGER:
	    return new FloatValue(CSSPrimitiveValue.CSS_NUMBER,
                                  lu.getIntegerValue());

	case LexicalUnit.SAC_REAL:
	    return new FloatValue(CSSPrimitiveValue.CSS_NUMBER,
                                  lu.getFloatValue());

	case LexicalUnit.SAC_PERCENTAGE:
	    return new FloatValue(CSSPrimitiveValue.CSS_PERCENTAGE,
                                  lu.getFloatValue());
        }
        throw createInvalidLexicalUnitDOMException(lu.getLexicalUnitType());
    }

    /**
     * Implements {@link ValueManager#createFloatValue(short,float)}.
     */
    public Value createFloatValue(short type, float floatValue)
        throws DOMException {
	switch (type) {
	case CSSPrimitiveValue.CSS_PERCENTAGE:
	case CSSPrimitiveValue.CSS_EMS:
	case CSSPrimitiveValue.CSS_EXS:
	case CSSPrimitiveValue.CSS_PX:
	case CSSPrimitiveValue.CSS_CM:
	case CSSPrimitiveValue.CSS_MM:
	case CSSPrimitiveValue.CSS_IN:
	case CSSPrimitiveValue.CSS_PT:
	case CSSPrimitiveValue.CSS_PC:
	case CSSPrimitiveValue.CSS_NUMBER:
	    return new FloatValue(type, floatValue);
	}
        throw createInvalidFloatTypeDOMException(type);
    }

    /**
     * Implements {@link
     * ValueManager#computeValue(CSSStylableElement,String,CSSEngine,int,StyleMap,Value)}.
     */
    public Value computeValue(CSSStylableElement elt,
                              String pseudo,
                              CSSEngine engine,
                              int idx,
                              StyleMap sm,
                              Value value) {
        if (value.getCssValueType() != CSSValue.CSS_PRIMITIVE_VALUE) {
            return value;
        }

        switch (value.getPrimitiveType()) {
        case CSSPrimitiveValue.CSS_NUMBER:
        case CSSPrimitiveValue.CSS_PX:
            return value;

        case CSSPrimitiveValue.CSS_MM:
            CSSContext ctx = engine.getCSSContext();
            float v = value.getFloatValue();
            return new FloatValue(CSSPrimitiveValue.CSS_NUMBER,
                                  v / ctx.getPixelToMillimeters());

        case CSSPrimitiveValue.CSS_CM:
            ctx = engine.getCSSContext(); 
            v = value.getFloatValue();
            return new FloatValue(CSSPrimitiveValue.CSS_NUMBER,
                                  v * 10f / ctx.getPixelToMillimeters());

        case CSSPrimitiveValue.CSS_IN:
            ctx = engine.getCSSContext();
            v = value.getFloatValue();
            return new FloatValue(CSSPrimitiveValue.CSS_NUMBER,
                                  v * 25.4f / ctx.getPixelToMillimeters());

        case CSSPrimitiveValue.CSS_PT:
            ctx = engine.getCSSContext();
            v = value.getFloatValue();
            return new FloatValue(CSSPrimitiveValue.CSS_NUMBER,
                                  v * 25.4f /
                                  (72f * ctx.getPixelToMillimeters()));

        case CSSPrimitiveValue.CSS_PC:
            ctx = engine.getCSSContext();
            v = value.getFloatValue();
            return new FloatValue(CSSPrimitiveValue.CSS_NUMBER,
                                  (v * 25.4f /
                                   (6f * ctx.getPixelToMillimeters())));

        case CSSPrimitiveValue.CSS_EMS:
            sm.putFontSizeRelative(idx, true);

            v = value.getFloatValue();
            int fsidx = engine.getFontSizeIndex();
            float fs;
            fs = engine.getComputedStyle(elt, pseudo, fsidx).getFloatValue();
            return new FloatValue(CSSPrimitiveValue.CSS_NUMBER, v * fs);


        case CSSPrimitiveValue.CSS_EXS:
            sm.putFontSizeRelative(idx, true);

            v = value.getFloatValue();
            fsidx = engine.getFontSizeIndex();
            fs = engine.getComputedStyle(elt, pseudo, fsidx).getFloatValue();
            return new FloatValue(CSSPrimitiveValue.CSS_NUMBER, v * fs * 0.5f);

        case CSSPrimitiveValue.CSS_PERCENTAGE:
            ctx = engine.getCSSContext();
            switch (getOrientation()) {
            case HORIZONTAL_ORIENTATION:
                sm.putBlockWidthRelative(idx, true);
                fs = value.getFloatValue() * ctx.getBlockWidth(elt) / 100f;
                break;
            case VERTICAL_ORIENTATION:
                sm.putBlockHeightRelative(idx, true);
                fs = value.getFloatValue() * ctx.getBlockHeight(elt) / 100f;
                break;
            default: // Both
                sm.putBlockWidthRelative(idx, true);
                sm.putBlockHeightRelative(idx, true);
                double w = ctx.getBlockWidth(elt);
                double h = ctx.getBlockHeight(elt);
                fs = (float)(value.getFloatValue() * 100.0 /
                             (Math.sqrt(w * w + h * h) / Math.sqrt(2)));
            }
            return new FloatValue(CSSPrimitiveValue.CSS_NUMBER, fs);
        }
        return value;
    }

    //
    // Orientation enumeration
    //
    protected final static int HORIZONTAL_ORIENTATION = 0;
    protected final static int VERTICAL_ORIENTATION = 1;
    protected final static int BOTH_ORIENTATION = 2;

    /**
     * Indicates the orientation of the property associated with
     * this manager.
     */
    protected abstract int getOrientation();
}