//////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2003, 2022 John Whaley and others
//
// See the CONTRIBUTORS file(s) distributed with this work for additional
// information regarding copyright ownership.
//
// This program and the accompanying materials are made available under the
// terms of the GNU Library General Public License v2 or later, which is
// available at https://spdx.org/licenses/LGPL-2.0-or-later.html
//
// SPDX-License-Identifier: LGPL-2.0-or-later
//////////////////////////////////////////////////////////////////////////////

package com.github.javabdd;

import java.math.BigInteger;

import com.github.javabdd.BDD.BDDIterator;

/**
 * Represents a domain of BDD variables. This is useful for finite state machines, among other things.
 *
 * <p>
 * BDDDomains are called "finite domain blocks" in Section 2.9 of the buddy documentation. A BDDDomain is a block of BDD
 * variables that can represent integer values as opposed to only true and false.
 * </p>
 *
 * <p>
 * Use {@code BDDFactory.extDomain()} to create one or more domains with a specified list of sizes.
 * </p>
 *
 * @see com.github.javabdd.BDDFactory#extDomain(int[])
 */
public abstract class BDDDomain {

    /* The name of this domain. */
    protected String name;

    /* The index of this domain. */
    protected int index;

    /* The specified domain (0...N-1) */
    protected BigInteger realsize;

    /* Variable indices for the variable set */
    protected int[] ivar;

    /* The BDD variable set. Actually constructed in extDomain(), etc. */
    protected BDDVarSet var;

    /**
     * Default constructor.
     *
     * @param index index of this domain
     * @param range size of this domain
     */
    protected BDDDomain(int index, BigInteger range) {
        BigInteger calcsize = BigInteger.valueOf(2L);
        if (range.signum() <= 0) {
            throw new BDDException();
        }
        this.name = Integer.toString(index);
        this.index = index;
        this.realsize = range;
        int binsize = 1;
        while (calcsize.compareTo(range) < 0) {
            binsize++;
            calcsize = calcsize.shiftLeft(1);
        }
        this.ivar = new int[binsize];
    }

    /**
     * Returns the factory that created this domain.
     *
     * @return the BDD factory
     */
    public abstract BDDFactory getFactory();

    /**
     * Sets the name of this domain.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the name of this domain.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the index of this domain.
     *
     * @return the index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns what corresponds to a disjunction of all possible values of this domain. This is more efficient than
     * doing ithVar(0) OR ithVar(1) ... explicitly for all values in the domain.
     *
     * <p>
     * Compare to fdd_domain.
     * </p>
     *
     * @return BDD representing the possible values of this domain
     */
    public BDD domain() {
        BDDFactory factory = getFactory();

        /* Encode V<=X-1. V is the variables in 'var' and X is the domain size */
        BigInteger val = size().subtract(BigInteger.ONE);
        BDD d = factory.universe();
        int[] ivar = vars();
        for (int n = 0; n < this.varNum(); n++) {
            if (val.testBit(0)) {
                d.orWith(factory.nithVar(ivar[n]));
            } else {
                d.andWith(factory.nithVar(ivar[n]));
            }
            val = val.shiftRight(1);
        }
        return d;
    }

    /**
     * Returns the size of the domain for this finite domain block.
     *
     * <p>
     * Compare to fdd_domainsize.
     * </p>
     *
     * @return the size
     */
    public BigInteger size() {
        return this.realsize;
    }

    public BDD buildAdd(BDDDomain that, long value) {
        if (this.varNum() != that.varNum()) {
            throw new BDDException();
        }
        return buildAdd(that, this.varNum(), value);
    }

    public BDD buildAdd(BDDDomain that, int bits, long value) {
        if (bits > this.varNum() || bits > that.varNum()) {
            throw new BDDException("Number of bits requested (" + bits + ") is larger than domain sizes "
                    + this.varNum() + "," + that.varNum());
        }

        BDDFactory bdd = getFactory();

        if (value == 0L) {
            BDD result = bdd.universe();
            int n;
            for (n = 0; n < bits; n++) {
                BDD b = bdd.ithVar(this.ivar[n]);
                b.biimpWith(bdd.ithVar(that.ivar[n]));
                result.andWith(b);
            }
            for (; n < Math.max(this.varNum(), that.varNum()); n++) {
                BDD b = (n < this.varNum()) ? bdd.nithVar(this.ivar[n]) : bdd.one();
                b.andWith((n < that.varNum()) ? bdd.nithVar(that.ivar[n]) : bdd.one());
                result.andWith(b);
            }
            return result;
        }

        int[] vars = new int[bits];
        System.arraycopy(this.ivar, 0, vars, 0, vars.length);
        BDDBitVector y = bdd.buildVector(vars);
        BDDBitVector v = bdd.constantVector(bits, value);
        BDDBitVector z = y.add(v);

        int[] thatvars = new int[bits];
        System.arraycopy(that.ivar, 0, thatvars, 0, thatvars.length);
        BDDBitVector x = bdd.buildVector(thatvars);
        BDD result = bdd.one();
        int n;
        for (n = 0; n < x.size(); n++) {
            BDD b = x.bitvec[n].biimp(z.bitvec[n]);
            result.andWith(b);
        }
        for (; n < Math.max(this.varNum(), that.varNum()); n++) {
            BDD b = (n < this.varNum()) ? bdd.nithVar(this.ivar[n]) : bdd.one();
            b.andWith((n < that.varNum()) ? bdd.nithVar(that.ivar[n]) : bdd.one());
            result.andWith(b);
        }
        x.free();
        y.free();
        z.free();
        v.free();
        return result;
    }

    /**
     * Builds a BDD which is true for all the possible assignments to the variable blocks that makes the blocks equal.
     *
     * <p>
     * Compare to fdd_equals/fdd_equ.
     * </p>
     *
     * @param that the other BDD domain
     * @return BDD
     */
    public BDD buildEquals(BDDDomain that) {
        if (!this.size().equals(that.size())) {
            throw new BDDException(
                    "Size of " + this + " != size of that " + that + "( " + this.size() + " vs " + that.size() + ")");
        }

        BDDFactory factory = getFactory();
        BDD e = factory.universe();

        int[] this_ivar = this.vars();
        int[] that_ivar = that.vars();

        for (int n = 0; n < this.varNum(); n++) {
            BDD a = factory.ithVar(this_ivar[n]);
            BDD b = factory.ithVar(that_ivar[n]);
            a.biimpWith(b);
            e.andWith(a);
        }

        return e;
    }

    /**
     * Returns the variable set that contains the variables used to define this finite domain block.
     *
     * <p>
     * Compare to fdd_ithset.
     * </p>
     *
     * @return BDDVarSet
     */
    public BDDVarSet set() {
        return var.id();
    }

    /**
     * Returns the BDD that defines the given value for this finite domain block.
     *
     * <p>
     * Compare to fdd_ithvar.
     * </p>
     *
     * @param val the given value
     * @return BDD
     */
    public BDD ithVar(long val) {
        return ithVar(BigInteger.valueOf(val));
    }

    public BDD ithVar(BigInteger val) {
        if (val.signum() < 0 || val.compareTo(size()) >= 0) {
            throw new BDDException(val + " is out of range");
        }

        BDDFactory factory = getFactory();
        BDD v = factory.universe();
        int[] ivar = this.vars();
        for (int n = 0; n < ivar.length; n++) {
            if (val.testBit(0)) {
                v.andWith(factory.ithVar(ivar[n]));
            } else {
                v.andWith(factory.nithVar(ivar[n]));
            }
            val = val.shiftRight(1);
        }

        return v;
    }

    /**
     * Returns the BDD that defines the given range of values, inclusive, for this finite domain block.
     *
     * @param lo low value (inclusive)
     * @param hi high value (inclusive)
     * @return BDD
     */
    public BDD varRange(long lo, long hi) {
        return varRange(BigInteger.valueOf(lo), BigInteger.valueOf(hi));
    }

    public BDD varRange(BigInteger lo, BigInteger hi) {
        if (lo.signum() < 0 || hi.compareTo(size()) >= 0 || lo.compareTo(hi) > 0) {
            throw new BDDException("range <" + lo + ", " + hi + "> is invalid");
        }

        BDDFactory factory = getFactory();
        BDD result = factory.zero();
        int[] ivar = this.vars();
        while (lo.compareTo(hi) <= 0) {
            BDD v = factory.universe();
            for (int n = ivar.length - 1;; n--) {
                if (lo.testBit(n)) {
                    v.andWith(factory.ithVar(ivar[n]));
                } else {
                    v.andWith(factory.nithVar(ivar[n]));
                }
                BigInteger mask = BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE);
                if (!lo.testBit(n) && lo.or(mask).compareTo(hi) <= 0) {
                    lo = lo.or(mask).add(BigInteger.ONE);
                    break;
                }
            }
            result.orWith(v);
        }
        return result;
    }

    /**
     * Returns the number of BDD variables used for this finite domain block.
     *
     * <p>
     * Compare to fdd_varnum.
     * </p>
     *
     * @return int
     */
    public int varNum() {
        return this.ivar.length;
    }

    /**
     * Returns an integer array containing the indices of the BDD variables used to define this finite domain.
     *
     * <p>
     * Compare to fdd_vars.
     * </p>
     *
     * @return int[]
     */
    public int[] vars() {
        return this.ivar;
    }

    public int ensureCapacity(long range) {
        return ensureCapacity(BigInteger.valueOf(range));
    }

    public int ensureCapacity(BigInteger range) {
        BigInteger calcsize = BigInteger.valueOf(2L);
        if (range.signum() < 0) {
            throw new BDDException();
        }
        if (range.compareTo(realsize) < 0) {
            return ivar.length;
        }
        this.realsize = range.add(BigInteger.ONE);
        int binsize = 1;
        while (calcsize.compareTo(range) <= 0) {
            binsize++;
            calcsize = calcsize.shiftLeft(1);
        }
        if (ivar.length == binsize) {
            return binsize;
        }

        throw new BDDException("Can't add bits to domains, requested domain " + name + " upper limit " + range);
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Convert a BDD {@code that} to a list of indices of this domain. This method assumes that the BDD passed is a
     * disjunction of ithVar(i_1) to ithVar(i_k). It returns an array of length 'k' with elements [i_1,...,i_k].
     *
     * <p>
     * Be careful when using this method for BDDs with a large number of entries, as it allocates a BigInteger[] array
     * of dimension k.
     * </p>
     *
     * @param that bdd that is the disjunction of domain indices
     * @return list of indices in this domain
     * @see #getVarIndices(BDD,int)
     * @see #ithVar(BigInteger)
     */
    public BigInteger[] getVarIndices(BDD that) {
        return getVarIndices(that, -1);
    }

    /**
     * Convert a BDD {@code that} to a list of indices of this domain. Same as getVarIndices(BDD), except only 'max'
     * indices are extracted.
     *
     * @param that bdd that is the disjunction of domain indices
     * @param max maximum number of entries to be returned
     * @return list of indices of this domain
     * @see #ithVar(long)
     */
    public BigInteger[] getVarIndices(BDD that, int max) {
        BDDVarSet myvarset = set(); // can't use var here, must respect subclass a factory may provide
        int n = (int)that.satCount(myvarset);
        if (max != -1 && n > max) {
            n = max;
        }
        BigInteger[] res = new BigInteger[n];
        BDDIterator it = that.iterator(myvarset);
        myvarset.free();
        for (int i = 0; i < n; i++) {
            res[i] = it.nextValue(this);
        }
        return res;
    }
}
