/*
 * Copyright (c) 2018 ACINO Consortium
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the Free Software Foundation License (version 3, or any later version).
 *
 * This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package es.upct.girtel.net2plan.plugins.onos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import es.upct.girtel.net2plan.plugins.onos.utils.Utils;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public final class GsonMessageBodyHandler implements MessageBodyWriter<Object>,
        MessageBodyReader<Object> {

    private static final String UTF_8 = "UTF-8";

    private Gson gson;

    private Gson getGson() {
        //System.out.println(Utils.getCurrentMethodName() + " called");
        if (gson == null) {
            final GsonBuilder gsonBuilder = new GsonBuilder();
            gson = gsonBuilder.create();
        }
        return gson;
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType,
                              java.lang.annotation.Annotation[] annotations, MediaType mediaType) {
       // System.out.println(Utils.getCurrentMethodName() + " called");
        return true;
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws WebApplicationException, IOException {
        InputStreamReader streamReader = new InputStreamReader(entityStream, UTF_8);
       // System.out.println(Utils.getCurrentMethodName() + " called");
        try {
            Type jsonType;
            if (type.equals(genericType)) {
                jsonType = type;
            } else {
                jsonType = genericType;
            }
            gson = getGson();
            //            System.out.println("GSON:readFrom -> trying to parse " + jsonType.getClass().getName() );
            // System.out.println("GSON:readFrom -> trying to parse " + jsonType.getTypeName());
            Object a = gson.fromJson(streamReader, jsonType);
            //System.out.println(Utils.getCurrentMethodName() + " returning object " + a.getClass());
            return a;
        } catch(WebApplicationException e){
         //   System.out.println(Utils.getCurrentMethodName() + " caught WebApplicationException! " + e.toString());
            throw e;
        } catch(JsonIOException e)
        {
           // System.out.println(Utils.getCurrentMethodName() + " Caught JSONIOException " + e.toString());
            throw new WebApplicationException("IO exception!", e.getCause(), Response.Status.BAD_REQUEST);
        } catch(JsonSyntaxException e) {
           // System.out.println(Utils.getCurrentMethodName() + " Caught JSONSyntaxException " + e.toString());
            throw new WebApplicationException("JSON Syntax ERROR!", e.getCause(), Response.Status.BAD_REQUEST);
        } catch(Exception e){
         //   System.out.println(Utils.getCurrentMethodName() + " Caught generic exception " + e.toString());
            throw new WebApplicationException("UKNOWN!" , e.getCause(), Response.Status.BAD_REQUEST);
        }
        finally {
            streamReader.close();
        }
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        //System.out.println(Utils.getCurrentMethodName() + " called");
        return true;
    }

    @Override
    public long getSize(Object object, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        //System.out.println(Utils.getCurrentMethodName() + " called");
        return -1;
    }

    @Override
    public void writeTo(Object object, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        System.out.println(Utils.getCurrentMethodName() + " called");
        OutputStreamWriter writer = new OutputStreamWriter(entityStream, UTF_8);
        try {
            Type jsonType;
            if (type.equals(genericType)) {
                jsonType = type;
            } else {
                jsonType = genericType;
            }
            getGson().toJson(object, jsonType, writer);
        } finally {
            writer.close();
        }
    }
}
