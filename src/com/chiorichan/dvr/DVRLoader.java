package com.chiorichan.dvr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.chiorichan.Loader;
import com.chiorichan.dvr.registry.InputRegistry;
import com.chiorichan.event.Listener;
import com.chiorichan.http.HttpResponse;
import com.chiorichan.http.HttpResponseStage;
import com.chiorichan.plugin.java.JavaPlugin;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamMotionEvent;
import com.github.sarxos.webcam.WebcamMotionListener;
import com.github.sarxos.webcam.ds.v4l4j.V4l4jDriver;
import com.github.sarxos.webcam.log.WebcamLogConfigurator;

public class DVRLoader extends JavaPlugin implements Listener, WebcamMotionListener
{
	private int captureId = -1;
	
	static DVRLoader instance;
	
	static
	{
		// If we are running on a Linux system, we want to use the Video4Linux4Java driver.
		if ( System.getProperty( "os.name" ).toLowerCase().contains( "linux" ) )
			Webcam.setDriver( new V4l4jDriver() );
	}
	
	public DVRLoader()
	{
		WebcamLogConfigurator.configure( DVRLoader.class.getClassLoader().getResourceAsStream( "com/chiorichan/dvr/logback.xml" ) );
		
		Loader.getLogger().info( "You are running OS: " + System.getProperty( "os.name" ) );
		
		instance = this;
	}
	
	public void startMultipart( final HttpResponse rep, int channel ) throws IOException
	{
		if ( channel < 0 || channel > InputRegistry.getInputCount() - 1 )
			channel = 0;
		
		final int channelNum = channel;
		
		rep.setContentType( "image/jpeg" );
		
		rep.sendMultipart( null );
		
		int _taskId = -1;
		
		class PushTask implements Runnable
		{
			int id = -1;
			
			@Override
			public void run()
			{
				if ( InputRegistry.get( channelNum ).getLastImage() != null )
				{
					try
					{
						ByteArrayOutputStream bs = new ByteArrayOutputStream();
						ImageIO.write( InputRegistry.get( channelNum ).getLastImage(), "JPG", bs );
						bs.flush();
						byte[] bss = bs.toByteArray();
						bs.close();
						
						rep.sendMultipart( bss );
					}
					catch ( IOException e )
					{
						if ( e.getMessage().toLowerCase().equals( "stream is closed" ) || e.getMessage().toLowerCase().equals( "broken pipe" ) )
						{
							Loader.getScheduler().cancelTask( id );
							try
							{
								rep.closeMultipart();
							}
							catch ( Exception e1 )
							{}
						}
						
						Loader.getLogger().warning( e.getMessage(), e );
					}
				}
				
				if ( rep.getStage() != HttpResponseStage.MULTIPART )
				{
					Loader.getScheduler().cancelTask( id );
					try
					{
						rep.closeMultipart();
					}
					catch ( Exception e1 )
					{}
				}
			}
			
			public void setId( int _taskId )
			{
				id = _taskId;
			}
		}
		;
		
		PushTask task = new PushTask();
		
		_taskId = Loader.getScheduler().scheduleSyncRepeatingTask( this, task, 1L, 1L );
		task.setId( _taskId );
	}
	
	public byte[] grabSnapshot( int channel ) throws IOException
	{
		if ( channel < 0 || channel > InputRegistry.getInputCount() - 1 )
			channel = 0;
		
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		ImageIO.write( InputRegistry.get( channel ).getLastImage(), "PNG", bs );
		bs.flush();
		byte[] bss = bs.toByteArray();
		bs.close();
		
		return bss;
	}
	
	public void onEnable()
	{
		Loader.getPluginManager().registerEvents( this, this );
		
		InputRegistry.findAllDevices();
		
		InputRegistry.openAllDevices();
		
		captureId = Loader.getScheduler().scheduleSyncRepeatingTask( this, new CapturingTask(), 2L, 2L );
	}
	
	public void onDisable()
	{
		Loader.getScheduler().cancelTask( captureId );
		InputRegistry.destroyAllDevices();
	}
	
	@Override
	public void motionDetected( WebcamMotionEvent arg0 )
	{
		// TODO Auto-generated method stub
		
	}
}
