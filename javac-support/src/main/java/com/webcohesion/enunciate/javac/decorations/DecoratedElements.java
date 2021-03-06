/**
 * Copyright © 2006-2016 Web Cohesion (info@webcohesion.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webcohesion.enunciate.javac.decorations;

import com.webcohesion.enunciate.javac.decorations.element.DecoratedAnnotationMirror;
import com.webcohesion.enunciate.javac.decorations.element.DecoratedElement;
import com.webcohesion.enunciate.javac.decorations.element.DecoratedExecutableElement;
import com.webcohesion.enunciate.javac.decorations.element.DecoratedTypeElement;
import com.webcohesion.enunciate.javac.javadoc.JavaDoc;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * @author Ryan Heaton
 */
public class DecoratedElements implements Elements {

  private final Elements delegate;
  private final ProcessingEnvironment env;

  public DecoratedElements(Elements delegate, ProcessingEnvironment env) {
    this.env = env;
    while (delegate instanceof DecoratedElements) {
      delegate = ((DecoratedElements) delegate).delegate;
    }
    this.delegate = delegate;
  }

  @Override
  public PackageElement getPackageElement(CharSequence name) {
    return ElementDecorator.decorate(delegate.getPackageElement(name), this.env);
  }

  @Override
  public TypeElement getTypeElement(CharSequence name) {
    return ElementDecorator.decorate(delegate.getTypeElement(name), this.env);
  }

  @Override
  public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(AnnotationMirror a) {
    while (a instanceof DecoratedAnnotationMirror) {
      a = ((DecoratedAnnotationMirror)a).getDelegate();
    }
    
    return delegate.getElementValuesWithDefaults(a);
  }

  @Override
  public String getDocComment(Element e) {
    while (e instanceof DecoratedElement) {
      e = ((DecoratedElement) e).getDelegate();
    }

    String docComment = delegate.getDocComment(e);
    if (docComment == null || docComment.trim().isEmpty() || docComment.contains("{@inheritDoc}")) {
      //look for inherited doc comments.
      docComment = findInheritedDocComment(e);
    }

    return docComment;
  }

  private String findInheritedDocComment(Element e) {
    //algorithm defined per http://docs.oracle.com/javase/6/docs/technotes/tools/solaris/javadoc.html#inheritingcomments
    while (e instanceof DecoratedElement) {
      e = ((DecoratedElement) e).getDelegate();
    }

    if (e instanceof TypeElement) {
      TypeElement te = (TypeElement) e;
      List<? extends TypeMirror> interfaces = te.getInterfaces();
      for (TypeMirror iface : interfaces) {
        Element el = iface instanceof DeclaredType ? ((DeclaredType)iface).asElement() : null;
        if (el != null) {
          String docComment = delegate.getDocComment(el);
          if (docComment != null && !docComment.trim().isEmpty()) {
            return docComment;
          }
        }
      }

      TypeMirror superclass = te.getSuperclass();
      if (superclass != null && superclass instanceof DeclaredType) {
        Element el = ((DeclaredType) superclass).asElement();
        if (el != null) {
          return getDocComment(el);
        }
      }
    }
    else if (e instanceof ExecutableElement) {
      Element el = e.getEnclosingElement();
      if (el instanceof TypeElement) {
        TypeElement typeElement = (TypeElement) el;
        List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
        for (TypeMirror iface : interfaces) {
          Element superType = iface instanceof DeclaredType ? ((DeclaredType) iface).asElement() : null;
          if (superType != null) {
            List<ExecutableElement> methods = ElementFilter.methodsIn(superType.getEnclosedElements());
            for (ExecutableElement candidate : methods) {
              if (delegate.overrides((ExecutableElement) e, candidate, typeElement)) {
                String docComment = delegate.getDocComment(candidate);
                if (docComment != null && !docComment.trim().isEmpty()) {
                  return docComment;
                }
              }
            }
          }
        }

        TypeMirror superclass = typeElement.getSuperclass();
        if (superclass != null && superclass instanceof DeclaredType) {
          Element superType = ((DeclaredType) superclass).asElement();
          if (superType != null) {
            List<ExecutableElement> methods = ElementFilter.methodsIn(superType.getEnclosedElements());
            for (ExecutableElement candidate : methods) {
              if (delegate.overrides((ExecutableElement) e, candidate, typeElement)) {
                String docComment = delegate.getDocComment(candidate);
                if (docComment != null && !docComment.trim().isEmpty()) {
                  return docComment;
                }
                else {
                  return findInheritedDocComment(candidate);
                }
              }
            }
          }
        }
      }
    }

    return null;
  }


  @Override
  public boolean isDeprecated(Element e) {
    while (e instanceof DecoratedElement) {
      e = ((DecoratedElement) e).getDelegate();
    }

    return delegate.isDeprecated(e);
  }

  @Override
  public Name getBinaryName(TypeElement type) {
    while (type instanceof DecoratedTypeElement) {
      type = ((DecoratedTypeElement) type).getDelegate();
    }

    return delegate.getBinaryName(type);
  }

  @Override
  public PackageElement getPackageOf(Element e) {
    while (e instanceof DecoratedElement) {
      e = ((DecoratedElement) e).getDelegate();
    }

    return ElementDecorator.decorate(delegate.getPackageOf(e), this.env);
  }

  @Override
  public List<? extends Element> getAllMembers(TypeElement type) {
    while (type instanceof DecoratedTypeElement) {
      type = ((DecoratedTypeElement) type).getDelegate();
    }

    return ElementDecorator.decorate(delegate.getAllMembers(type), this.env);
  }

  @Override
  public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element e) {
    while (e instanceof DecoratedElement) {
      e = ((DecoratedElement) e).getDelegate();
    }

    return ElementDecorator.decorateAnnotationMirrors(delegate.getAllAnnotationMirrors(e), this.env);
  }

  @Override
  public boolean hides(Element hider, Element hidden) {
    while (hider instanceof DecoratedElement) {
      hider = ((DecoratedElement) hider).getDelegate();
    }

    while (hidden instanceof DecoratedElement) {
      hidden = ((DecoratedElement) hidden).getDelegate();
    }

    return delegate.hides(hider, hidden);
  }

  @Override
  public boolean overrides(ExecutableElement overrider, ExecutableElement overridden, TypeElement type) {
    while (overrider instanceof DecoratedExecutableElement) {
      overrider = ((DecoratedExecutableElement) overrider).getDelegate();
    }

    while (overridden instanceof DecoratedExecutableElement) {
      overridden = ((DecoratedExecutableElement) overridden).getDelegate();
    }

    while (type instanceof DecoratedTypeElement) {
      type = ((DecoratedTypeElement) type).getDelegate();
    }

    return delegate.overrides(overrider, overridden, type);
  }

  @Override
  public String getConstantExpression(Object value) {
    return delegate.getConstantExpression(value);
  }

  @Override
  public void printElements(Writer w, Element... elements) {
    Element[] copy = new Element[elements.length];
    for (int i = 0; i < elements.length; i++) {
      Element e = elements[i];
      while (e instanceof DecoratedElement) {
        e = ((DecoratedElement) e).getDelegate();
      }

      copy[i] = e;
    }

    delegate.printElements(w, copy);
  }

  @Override
  public Name getName(CharSequence cs) {
    return delegate.getName(cs);
  }

}
