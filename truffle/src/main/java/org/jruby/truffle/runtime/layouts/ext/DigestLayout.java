/*
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.runtime.layouts.ext;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import org.jruby.truffle.om.dsl.api.Layout;
import org.jruby.truffle.runtime.layouts.BasicObjectLayout;

import java.security.MessageDigest;

@Layout
public interface DigestLayout extends BasicObjectLayout {

    DynamicObjectFactory createDigestShape(DynamicObject logicalClass,
                                           DynamicObject metaClass);

    DynamicObject createDigest(DynamicObjectFactory factory,
                               MessageDigest digest);

    MessageDigest getDigest(DynamicObject object);

}
