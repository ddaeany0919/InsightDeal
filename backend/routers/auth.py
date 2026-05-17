from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import Optional
import hashlib
from datetime import datetime

from backend.database.session import get_db_session
from backend.database import models

router = APIRouter()

class UserCreate(BaseModel):
    username: str
    password: str
    nickname: str

class UserLogin(BaseModel):
    username: str
    password: str

class UserResponse(BaseModel):
    username: str
    nickname: str
    honey_points: int
    
    class Config:
        orm_mode = True

def hash_password(password: str) -> str:
    return hashlib.sha256(password.encode()).hexdigest()

@router.post("/signup", response_model=UserResponse)
def signup(user: UserCreate, db: Session = Depends(get_db_session)):
    # Check if username exists
    existing_user = db.query(models.User).filter(models.User.username == user.username).first()
    if existing_user:
        raise HTTPException(status_code=400, detail="Username already registered")
        
    # Check if nickname exists
    existing_nick = db.query(models.User).filter(models.User.nickname == user.nickname).first()
    if existing_nick:
        raise HTTPException(status_code=400, detail="Nickname already taken")

    new_user = models.User(
        username=user.username,
        password_hash=hash_password(user.password),
        nickname=user.nickname
    )
    
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    
    return new_user

@router.post("/login", response_model=UserResponse)
def login(user: UserLogin, db: Session = Depends(get_db_session)):
    db_user = db.query(models.User).filter(models.User.username == user.username).first()
    if not db_user:
        raise HTTPException(status_code=400, detail="Invalid username or password")
        
    if db_user.password_hash != hash_password(user.password):
        raise HTTPException(status_code=400, detail="Invalid username or password")
        
    return db_user
