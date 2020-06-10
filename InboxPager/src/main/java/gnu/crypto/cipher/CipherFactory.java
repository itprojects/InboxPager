/*
 * Copyright (C) 2001, 2002, 2003 Free Software Foundation, Inc.
 *
 * This file is part of GNU Crypto.
 *
 * GNU Crypto is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Crypto is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; see the file COPYING.  If not, write to the
 *
 *    Free Software Foundation Inc.,
 *    59 Temple Place - Suite 330,
 *    Boston, MA 02111-1307
 *    USA
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give
 * you permission to link this library with independent modules to
 * produce an executable, regardless of the license terms of these
 * independent modules, and to copy and distribute the resulting
 * executable under terms of your choice, provided that you also meet,
 * for each linked independent module, the terms and conditions of the
 * license of that module.  An independent module is a module which is
 * not derived from or based on this library.  If you modify this
 * library, you may extend this exception to your version of the
 * library, but you are not obligated to do so.  If you do not wish to
 * do so, delete this exception statement from your version.
 **/
package gnu.crypto.cipher;

import gnu.crypto.Registry;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/*
 * <p>A <i>Factory</i> to instantiate symmetric block cipher instances.</p>
 *
 * @version $Revision: 1.11 $
 **/
public class CipherFactory implements Registry {

   private CipherFactory() {
      super();
   }

   /**
    * <p>Returns an instance of a block cipher given its name.</p>
    *
    * @param name the case-insensitive name of the symmetric-key block cipher
    * algorithm.
    * @return an instance of the designated cipher algorithm, or
    * <code>null</code> if none is found.
    * @exception InternalError if the implementation does not pass its
    * self-test.
    */
   public static final IBlockCipher getInstance(String name) {
      if (name == null) {
         return null;
      }

      name = name.trim();
      IBlockCipher result = null;
      if (name.equalsIgnoreCase(RIJNDAEL_CIPHER)
              || name.equalsIgnoreCase(AES_CIPHER)) {
         result = new Rijndael();
      } else if (name.equalsIgnoreCase(TWOFISH_CIPHER)) {
         result = new Twofish();
      } else if (name.equalsIgnoreCase(NULL_CIPHER)) {
         result = new NullCipher();
      }

      if (result != null && !result.selfTest()) {
         throw new InternalError(result.name());
      }

      return result;
   }

   /**
    * <p>Returns a {@link Set} of symmetric key block cipher implementation
    * names supported by this <i>Factory</i>.</p>
    *
    * @return a {@link Set} of block cipher names (Strings).
    */
   public static final Set<String> getNames() {
      HashSet<String> hs = new HashSet<>();
      hs.add(RIJNDAEL_CIPHER);// AES
      hs.add(TWOFISH_CIPHER);
      hs.add(NULL_CIPHER);

      return Collections.unmodifiableSet(hs);
   }
}
