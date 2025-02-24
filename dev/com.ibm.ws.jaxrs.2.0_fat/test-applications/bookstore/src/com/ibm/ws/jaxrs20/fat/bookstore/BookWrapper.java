/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.jaxrs20.fat.bookstore;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "wrapper", namespace = "http://books")
public class BookWrapper {
    private Book book;

    public BookWrapper() {

    }

    public void setBook(Book b) {
        this.book = b;
    }

    public Book getBook() {
        return book;
    }
}
