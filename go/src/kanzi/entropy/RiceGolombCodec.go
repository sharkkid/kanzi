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

package entropy

import (
	"kanzi"
	"errors"
)

type RiceGolombEncoder struct {
	signed    bool
	logBase   uint
	base      uint
	bitstream kanzi.OutputBitStream
}

// If sgn is true, the input value is turned into an int8
// Managing sign improves compression ratio for distributions centered on 0 (E.G. Gaussian)
// Example: -1 is better compressed as int8 (1 followed by -) than as byte (-1 & 255 = 255)
func NewRiceGolombEncoder(bs kanzi.OutputBitStream, sgn bool, logBase uint) (*RiceGolombEncoder, error) {
	if bs == nil {
		return nil, errors.New("Bit stream parameter cannot be null")
	}

	if logBase <= 0 || logBase >= 8 {
		return nil, errors.New("Invalid logBase value (must be in [1..7])")
	}

	this := new(RiceGolombEncoder)
	this.signed = sgn
	this.bitstream = bs
	this.logBase = logBase
	this.base = 1 << logBase
	return this, nil
}

func (this *RiceGolombEncoder) Signed() bool {
	return this.signed
}

func (this *RiceGolombEncoder) Dispose() {
}

func (this *RiceGolombEncoder) EncodeByte(val byte) error {
	if val == 0 {
		_, err := this.bitstream.WriteBits(uint64(this.base), uint(this.logBase+1))
		return err
	}

	var val2 byte

	if this.signed == true {
		sVal := int8(val)
		//  Take the abs() of 'sVal' 
		val2 = byte((sVal + (sVal >> 7)) ^ (sVal >> 7))
	} else {
		val2 = val
	}

	// quotient is unary encoded, rest is binary encoded
	emit := uint64(this.base | (uint(val2) & (this.base - 1)))
	n := uint((1 + (uint(val2) >> this.logBase)) + this.logBase)

	if this.signed == true {
		// Add 0 for positive and 1 for negative sign (considering
		// msb as byte 'sign')
		n++
		emit = (emit << 1) | uint64((val>>7)&1)
	}

	_, err := this.bitstream.WriteBits(emit, n)
	return err
}

func (this *RiceGolombEncoder) BitStream() kanzi.OutputBitStream {
	return this.bitstream
}

func (this *RiceGolombEncoder) Encode(block []byte) (int, error) {
	return EntropyEncodeArray(this, block)
}

type RiceGolombDecoder struct {
	signed    bool
	logBase   uint
	bitstream kanzi.InputBitStream
}

// If sgn is true, the extracted value is treated as an int8
func NewRiceGolombDecoder(bs kanzi.InputBitStream, sgn bool, logBase uint) (*RiceGolombDecoder, error) {
	if bs == nil {
		return nil, errors.New("Bit stream parameter cannot be null")
	}

	if bs == nil {
		return nil, errors.New("Bit stream parameter cannot be null")
	}

	if logBase <= 0 || logBase >= 8 {
		return nil, errors.New("Invalid logBase value (must be in [1..7])")
	}

	this := new(RiceGolombDecoder)
	this.signed = sgn
	this.bitstream = bs
	this.logBase = logBase
	return this, nil
}
func (this *RiceGolombDecoder) Signed() bool {
	return this.signed
}

func (this *RiceGolombDecoder) Dispose() {
}

// If the decoder is signed, the returned value is a byte encoded int8
func (this *RiceGolombDecoder) DecodeByte() (byte, error) {
	q := 0
	bit, err := this.bitstream.ReadBit()

	if err != nil {
		return byte(0), err
	}

	// quotient is unary encoded
	for bit == 0 {
		q++
		bit, err = this.bitstream.ReadBit()

		if err != nil {
			return byte(0), err
		}
	}

	// remainder is binary encoded
	r, err2 := this.bitstream.ReadBits(this.logBase)

	if err2 != nil {
		return byte(0), err2
	}

	res := (q << this.logBase) | int(r)

	if res != 0 && this.signed == true {
		// If res != 0, Get the 'sign', encoded as 1 for 'negative values'
		bit, err = this.bitstream.ReadBit()

		if err != nil {
			return byte(res), err
		}

		if bit == 1 {
			sVal := int8(-res)
			return byte(sVal), nil
		}
	}

	return byte(res), nil
}

func (this *RiceGolombDecoder) BitStream() kanzi.InputBitStream {
	return this.bitstream
}

func (this *RiceGolombDecoder) Decode(block []byte) (int, error) {
	return EntropyDecodeArray(this, block)
}
