/*
Copyright 2011-2013 Frederic Langlet
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
you may obtain a copy of the License at

                http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package kanzi.test;

import java.util.Arrays;
import java.util.Random;
import kanzi.IndexedByteArray;
import kanzi.function.ZLT;


public class TestZLT
{
   public static void main(String[] args)
   {
      System.out.println("TestZLT");
      byte[] input;
      byte[] output;
      byte[] res;
      Random rnd = new Random();

      // Test behavior
      for (int ii=0; ii<=20; ii++)
      {
         System.out.println("\n\nCorrectness test "+ii);
         int[] arr = new int[64];

         for (int i=0; i<arr.length; i++)
         {
             int val = rnd.nextInt(100);

             if (val >= 33)
                 val = 0;

             arr[i] = val;
         }

         int size = arr.length; // Must be a multiple of 8 (to make decoding easier)
         input = new byte[size];
         output = new byte[size];
         res = new byte[size];
         IndexedByteArray iba1 = new IndexedByteArray(input, 0);
         IndexedByteArray iba2 = new IndexedByteArray(output, 0);
         IndexedByteArray iba3 = new IndexedByteArray(res, 0);
         Arrays.fill(output, (byte) 0xAA);

         for (int i = 0; i < arr.length; i++)
         {
            input[i] = (byte) (arr[i] & 255);
         }

         ZLT zlt = new ZLT();
         System.out.println("\nOriginal: ");

         for (int i = 0; i < input.length; i++)
         {
            System.out.print((input[i] & 255) + " ");
         }

         if (zlt.forward(iba1, iba2) == false)
         {
            System.out.println("\nEncoding error");
            System.exit(1);
         }            

         if (iba1.index != input.length)
         {
            System.out.println("\nNo compression (ratio > 1.0), skip reverse");
            continue;
         }
         
         System.out.println("\nCoded: ");

         for (int i = 0; i < iba2.index; i++)
         {
            System.out.print((output[i] & 255) + " "); //+"("+Integer.toBinaryString(output[i] & 255)+") ");
         }

         System.out.print(" (Compression ratio: " + (iba2.index * 100 / input.length)+ "%)");
         zlt = new ZLT(); // Required to reset internal attributes
         iba1.index = 0;
         iba2.index = 0;
         iba3.index = 0;
         
         if (zlt.inverse(iba2, iba3) == false)
         {
            System.out.println("\nDecoding error");
            System.exit(1);
         }

         System.out.println("\nDecoded: ");
         boolean ok = true;

         for (int i = 0; i < input.length; i++)
         {
            System.out.print((res[i] & 255) + " ");

            if (res[i] != input[i])
                ok = false;
         }

         System.out.print((ok == true) ? "\nIdentical" : "\nDifferent");

         System.out.println();
      }

      // Test speed
      final int iter = 50000;
      final int size = 30000;
      System.out.println("\n\nSpeed test");
      System.out.println("Iterations: "+iter);
      
      for (int jj=0; jj<3; jj++)
      {
         input = new byte[size];
         output = new byte[size];
         res = new byte[size];
         IndexedByteArray iba1 = new IndexedByteArray(input, 0);
         IndexedByteArray iba2 = new IndexedByteArray(output, 0);
         IndexedByteArray iba3 = new IndexedByteArray(res, 0);

         // Generate random data with runs
         int n = 0;
                 
         while (n < input.length)        
         {
            byte val = (byte) (rnd.nextInt() & 3);
            input[n++] = val;
            int run = rnd.nextInt() & 255;
            run -= 200;
            
            while ((--run > 0) && (n < input.length))       
               input[n++] = val;
         }
         
         ZLT zlt = new ZLT();
         long before, after;
         long delta1 = 0;
         long delta2 = 0;
         
         for (int ii = 0; ii < iter; ii++)
         {
            zlt = new ZLT(); // Required to reset internal attributes
            iba1.index = 0;
            iba2.index = 0;
            before = System.nanoTime();

            if (zlt.forward(iba1, iba2) == false)
            {
               System.out.println("Encoding error");
               System.exit(1);
            }
               
            after = System.nanoTime();
            delta1 += (after - before);
         }
         
         for (int ii = 0; ii < iter; ii++)
         {
            zlt = new ZLT(); // Required to reset internal attributes
            iba2.index = 0;
            iba3.index = 0;
            before = System.nanoTime();
            
            if (zlt.inverse(iba2, iba3) == false)
            {
               System.out.println("Decoding error");
               System.exit(1);
            }

            after = System.nanoTime();
            delta2 += (after - before);
         }

         int idx = -1;
         
         // Sanity check
         for (int i=0; i<iba1.index; i++)
         {
            if (iba1.array[i] != iba3.array[i])
            {
               idx = i;
               break;
            }
         }
         
         if (idx >= 0)
            System.out.println("Failure at index "+idx+" ("+iba1.array[idx]+"<->"+iba3.array[idx]+")");

         System.out.println("ZLT encoding [ms] : " + delta1 / 1000000);
         System.out.println("Throughput [MB/s] : " + ((long) (iter*size)) * 1000000L / delta1 * 1000L / (1024*1024));
         System.out.println("ZLT decoding [ms] : " + delta2 / 1000000);
         System.out.println("Throughput [MB/s] : " +((long) (iter*size)) * 1000000L / delta2 * 1000L / (1024*1024));
      }
   }
}