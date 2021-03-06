/*
 * Copyright 2007-2010 by the authors indicated in the @author tags.
 * All rights reserved.
 *
 * See the LICENSE file for details.
 * 
 */
package org.zamia.plugin.editors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.zamia.vhdl.ast.Architecture;
import org.zamia.vhdl.ast.Block;
import org.zamia.vhdl.ast.BlockDeclarativeItem;
import org.zamia.vhdl.ast.ConcurrentStatement;
import org.zamia.vhdl.ast.Entity;
import org.zamia.vhdl.ast.GenerateStatement;
import org.zamia.vhdl.ast.InstantiatedUnit;
import org.zamia.vhdl.ast.InterfaceDeclaration;
import org.zamia.vhdl.ast.InterfaceList;
import org.zamia.vhdl.ast.PackageBody;
import org.zamia.vhdl.ast.Range;
import org.zamia.vhdl.ast.SequentialProcess;
import org.zamia.vhdl.ast.SubProgram;
import org.zamia.vhdl.ast.VHDLNode;
import org.zamia.vhdl.ast.VHDLPackage;


/**
 * Outline page content provider, uses the zamia compilers to generate syntax
 * tree nodes in the outline view
 * 
 * @author Guenter Bartsch
 * 
 */

public class ZamiaOutlineContentProvider implements ITreeContentProvider {

	private boolean fHierarchicalMode = false;

	private boolean fDoSort = false;

	private ZamiaEditor fEditor;

	public ZamiaOutlineContentProvider(ZamiaEditor aEditor) {
		fEditor = aEditor;
	}

	public void dispose() {
	}

	@SuppressWarnings("unchecked")
	private void sort(ArrayList aList) {
		Collections.sort(aList, new Comparator() {
			public int compare(Object o1, Object o2) {
				return o1.toString().compareToIgnoreCase(o2.toString());
			}
		});
	}

	@SuppressWarnings("unchecked")
	public Object[] getChildren(Object aElement) {

		if (aElement instanceof Entity) {
			Entity entity = (Entity) aElement;

			if (fHierarchicalMode) {
				ZamiaOutlineHierarchyGen hier = new ZamiaOutlineHierarchyGen();

				for (BlockDeclarativeItem decl : entity.fDeclarations) {
					hier.add(decl);
				}

				InterfaceList ports = entity.getPorts();
				if (ports != null) for (InterfaceDeclaration idecl : ports) {
					hier.add(idecl);
				}

				return hier.toArray();
			} else {

				int n = entity.getNumInterfaceDeclarations();
				ArrayList<InterfaceDeclaration> l = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					l.add(entity.getInterfaceDeclaration(i));
				}
				if (fDoSort) {
					sort(l);
				}
				return l.toArray();
			}
		} else if (aElement instanceof Architecture) {
			Architecture arch = (Architecture) aElement;

			if (fHierarchicalMode) {
				ZamiaOutlineHierarchyGen hier = new ZamiaOutlineHierarchyGen();

				for (BlockDeclarativeItem decl : arch.fDeclarations) {
					hier.add(decl);
				}

				int n = arch.getNumConcurrentStatements();
				for (int i = 0; i < n; i++) {
					ConcurrentStatement cs = arch.getConcurrentStatement(i);
					extractCS(cs, hier);
				}

				return hier.toArray();
				
			} else {

				ArrayList<Object> l = new ArrayList<>(arch.getNumDeclarations());

				for (BlockDeclarativeItem decl : arch.fDeclarations) l.add(decl);

				int n = arch.getNumConcurrentStatements();
				for (int i = 0; i < n; i++) filterCS(arch.getConcurrentStatement(i), l);

				if (fDoSort) sort(l);
				
				return l.toArray();
			}

		} else if (aElement instanceof Block || aElement instanceof GenerateStatement) {
			ConcurrentStatement parent = (ConcurrentStatement) aElement;
			int n = parent.getNumChildren(); ArrayList<Object> list = new ArrayList<>(n); 
			for (int i = 0; i < n; i++) filterCS(parent.getChild(i), list);
			return list.toArray();
		} 

		
		else if (aElement instanceof VHDLPackage) {
			VHDLPackage pkg = (VHDLPackage) aElement;

			if (fHierarchicalMode) {
				ZamiaOutlineHierarchyGen hier = new ZamiaOutlineHierarchyGen();

				for (BlockDeclarativeItem decl : pkg.fDeclarations) {
					hier.add(decl);
				}

				return hier.toArray();
			} else {

				ArrayList<BlockDeclarativeItem> l = new ArrayList<>(pkg.getNumDeclarations());

				for (BlockDeclarativeItem decl : pkg.fDeclarations) {
					l.add(decl);
				}
				if (fDoSort) {
					sort(l);
				}
				return l.toArray();
			}
		} else if (aElement instanceof SequentialProcess) {
			SequentialProcess proc = (SequentialProcess) aElement;

			int m = proc.getNumDeclarations();

			Object ret[] = new Object[m];
			int j = 0;

			for (int i = 0; i < m; i++) {
				BlockDeclarativeItem decl = proc.getDeclaration(i);
				ret[j++] = decl;
			}
			return ret;
		} else if (aElement instanceof SubProgram) {
			SubProgram sub = (SubProgram) aElement;

			int m = sub.getNumDeclarations();

			Object ret[] = new Object[m];
			int j = 0;

			for (int i = 0; i < m; i++) {
				BlockDeclarativeItem decl = sub.getDeclaration(i);
				ret[j++] = decl;
			}
			return ret;
		} else if (aElement instanceof PackageBody) {
			PackageBody pkg = (PackageBody) aElement;

			Object ret[] = new Object[pkg.getNumDeclarations()];
			int j = 0;
			for (BlockDeclarativeItem decl : pkg.fDeclarations) {
				ret[j++] = decl;
			}
			return ret;

		} else if (aElement instanceof ZamiaOutlineFolder) {
			ZamiaOutlineFolder folder = (ZamiaOutlineFolder) aElement;
			return folder.items.toArray();
		}

		return null;
	}

	void filterCS(VHDLNode item, Collection<Object> col) {
		Supplier<Boolean> invisibleSmt = () -> {
			String name = ((ConcurrentStatement) item).getLabel();
			return name == null || name.length() == 0;
		};
		if (!(item == null || item instanceof Range || (item instanceof ConcurrentStatement && invisibleSmt.get()))) 
			col.add(item);
	}
	
	private void extractCS(ConcurrentStatement aCS, ZamiaOutlineHierarchyGen aHier) {
		if (aCS instanceof SequentialProcess) {
			aHier.add(aCS);
		} else if (aCS instanceof InstantiatedUnit) {
			aHier.add(aCS);
		} else if (aCS instanceof GenerateStatement) {

			GenerateStatement gs = (GenerateStatement) aCS;

			int n = gs.getNumConcurrentStatements();
			for (int i = 0; i < n; i++) {
				ConcurrentStatement cs2 = gs.getConcurrentStatement(i);
				extractCS(cs2, aHier);
			}
		} else if (aCS instanceof Block) {
			Block block = (Block) aCS;
			int n = block.getNumConcurrentStatements();
			for (int i = 0; i < n; i++) {
				ConcurrentStatement cs2 = block.getConcurrentStatement(i);
				extractCS(cs2, aHier);
			}
			
		} 
	}

	public boolean hasChildren(Object aElement) {

		if (aElement instanceof SequentialProcess)
			return ((SequentialProcess) aElement).getNumDeclarations() > 0;
		if (aElement instanceof Block) return getChildren(aElement).length > 0;
		if (aElement instanceof GenerateStatement) return getChildren(aElement).length > 0;
		return aElement instanceof Entity || aElement instanceof Architecture
				|| aElement instanceof VHDLPackage || aElement instanceof PackageBody 
				|| aElement instanceof ZamiaOutlineFolder || aElement instanceof SubProgram;
	}

	public Object[] getElements(Object aInputElement) {

		if (fEditor.isDirty()) return new Object[] {};
		ZamiaReconcilingStrategy strategy = fEditor.getReconcilingStrategy();
		return strategy.getRootElements();
	}

	public void inputChanged(Viewer aViewer, Object anOldInput, Object aNewInput) {
	}

	public Object getParent(Object aElement) {
		// FIXME return (anElement instanceof ITreeNode) ? ((ITreeNode)
		// anElement).getParent() : null;
		return null;
	}

}