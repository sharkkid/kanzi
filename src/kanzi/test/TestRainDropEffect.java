/*
Copyright 2011 Frederic Langlet
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

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import kanzi.Global;
import kanzi.filter.RainDropEffect;


public class TestRainDropEffect
{   
   @SuppressWarnings("SleepWhileInLoop")
    public static void main(String[] args)
    {
        try
        {
            String fileName = (args.length > 0) ? args[0] : "c:\\temp\\lena.jpg";
            ImageIcon icon = new ImageIcon(fileName);
            Image image = icon.getImage();
            int w = image.getWidth(null);
            int h = image.getHeight(null);
            System.out.println(w+"x"+h);
            GraphicsDevice gs = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[0];
            GraphicsConfiguration gc = gs.getDefaultConfiguration();
            BufferedImage img = gc.createCompatibleImage(w, h, Transparency.OPAQUE);
            img.getGraphics().drawImage(image, 0, 0, null);
            BufferedImage img2 = gc.createCompatibleImage(w, h, Transparency.OPAQUE);
            int[] source = new int[w*h];
            int[] dest = new int[w*h];

            // Do NOT use img.getRGB(): it is more than 10 times slower than
            // img.getRaster().getDataElements()
            img.getRaster().getDataElements(0, 0, w, h, source);

            RainDropEffect effect = new RainDropEffect(w, h, 0, w, 400, 30, 70, 0);
            //effect.addDrop(300, 300, 250);
            effect.apply(source, dest);
            img2.getRaster().setDataElements(0, 0, w, h, dest);

            //icon = new ImageIcon(img);
            JFrame frame = new JFrame("Original");
            frame.setBounds(150, 100, w, h);
            frame.add(new JLabel(icon));
            frame.setVisible(true);
            JFrame frame2 = new JFrame("Filter");
            frame2.setBounds(700, 150, w, h);
            ImageIcon newIcon = new ImageIcon(img2);
            frame2.add(new JLabel(newIcon));
            frame2.setVisible(true);

            while (effect.getAmplitude() > 0)
            {
                effect.setAmplitude(effect.getAmplitude()-1);
                effect.setPhase((effect.getPhase()+500)%Global.PI_1024_MULT2);
                effect.apply(source, dest);
                img2.getRaster().setDataElements(0, 0, w, h, dest);
                frame2.invalidate();
                frame2.repaint();
                Thread.sleep(80);
            }
            
            // Speed test
            {
                int[] tmp = new int[w*h];
                System.arraycopy(source, 0, tmp, 0, w * h);
                System.out.println("Speed test");
                int iters = 1000;
                long before = System.nanoTime();

                for (int ii=0; ii<iters; ii++)
                {
                   effect.apply(source, tmp);
                }

                long after = System.nanoTime();
                System.out.println("Elapsed [ms]: "+ (after-before)/1000000+" ("+iters+" iterations)");
            }

            try
            {
                Thread.sleep(15000);
            }
            catch (Exception e)
            {
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        System.exit(0);
    }
}
